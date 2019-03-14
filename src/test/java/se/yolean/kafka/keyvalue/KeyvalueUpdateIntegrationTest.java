package se.yolean.kafka.keyvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.Properties;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyvalueUpdateIntegrationTest {

  private TopologyTestDriver testDriver;
  private ConsumerRecordFactory<String, byte[]> recordFactory = new ConsumerRecordFactory<>(new StringSerializer(),
      new ByteArraySerializer());

  private static final String TOPIC1 = "topic1";
  private KeyvalueUpdate cache = null;
  private OnUpdateRecordInMemory onUpdate = new OnUpdateRecordInMemory();

  @BeforeEach
  public void setup() {
    cache = new KeyvalueUpdateProcessor(TOPIC1, onUpdate);

    Properties config = new Properties();
    config.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "test-kafka-keyvalue");
    config.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

    Topology topology = cache.getTopology();

    testDriver = new TopologyTestDriver(topology, config);
  }

  @AfterEach
  public void tearDown() {
    testDriver.close();
  }

  @Test
  void testBasicFlow() {
    ((KeyvalueUpdateProcessor) cache).setInitTimestampForOnupdateSuppression(1552134516000L);

    assertEquals(null, cache.getValue("k1"));
    assertEquals(null, cache.getCurrentOffset(TOPIC1, 0));

    testDriver.pipeInput(recordFactory.create(TOPIC1, "k1", "v1".getBytes()));

    assertEquals(null, cache.getValue("k0"));

    byte[] v1 = cache.getValue("k1");
    assertNotNull(v1);
    assertEquals("v1", new String(v1));
    assertEquals(1, onUpdate.getAll().size());

    UpdateRecord update1 = onUpdate.getAll().get(0);
    assertEquals(TOPIC1, update1.getTopic());
    assertEquals(0, update1.getPartition());
    assertEquals(0, update1.getOffset());
    assertEquals("k1", update1.getKey());

    assertEquals(0, cache.getCurrentOffset(TOPIC1, 0));
    assertEquals(null, cache.getCurrentOffset("othertopic", 0));

    testDriver.pipeInput(recordFactory.create(TOPIC1, "k1", "v2".getBytes()));
    byte[] v2 = cache.getValue("k1");
    assertEquals("v2", new String(v2));
    assertEquals(2, onUpdate.getAll().size());

    UpdateRecord update2 = onUpdate.getAll().get(1);
    assertEquals(TOPIC1, update2.getTopic());
    assertEquals(0, update2.getPartition());
    assertEquals(1, update2.getOffset());
    assertEquals("k1", update2.getKey());
    assertEquals(0, update1.getOffset());
    assertEquals(1, cache.getCurrentOffset(TOPIC1, 0));
    assertEquals(null, cache.getCurrentOffset(TOPIC1, 1));

    Iterator<String> keys = cache.getKeys();
    assertTrue(keys.hasNext());
    assertEquals("k1", keys.next());
    assertFalse(keys.hasNext());

    Iterator<byte[]> values = cache.getValues();
    assertTrue(values.hasNext());
    assertEquals("v2", new String(values.next()));
    assertFalse(values.hasNext());
  }

  @Test
  void testBasicFlowWithHistoricMessage() {
    long processorInitTime = 1552134516000L;
    ((KeyvalueUpdateProcessor) cache).setInitTimestampForOnupdateSuppression(processorInitTime);

    assertEquals(null, cache.getValue("k1"));
    assertEquals(null, cache.getCurrentOffset(TOPIC1, 0));

    testDriver.pipeInput(recordFactory.create(TOPIC1, "k1", "v1".getBytes(), processorInitTime - 1));

    assertEquals(null, cache.getValue("k0"));

    byte[] v1 = cache.getValue("k1");
    assertNotNull(v1);
    assertEquals("v1", new String(v1));
    assertEquals(0, onUpdate.getAll().size());

    testDriver.pipeInput(recordFactory.create(TOPIC1, "k2", "v2".getBytes(), processorInitTime));

    byte[] v2 = cache.getValue("k2");
    assertNotNull(v2);
    assertEquals("v2", new String(v2));
    assertEquals(1, onUpdate.getAll().size());

    assertEquals(1, cache.getCurrentOffset(TOPIC1, 0));
    assertEquals(null, cache.getCurrentOffset("othertopic", 0));
  }

}
