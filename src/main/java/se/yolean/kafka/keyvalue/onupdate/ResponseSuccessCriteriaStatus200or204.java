package se.yolean.kafka.keyvalue.onupdate;

import javax.ws.rs.core.Response;

public class ResponseSuccessCriteriaStatus200or204 implements ResponseSuccessCriteria {

  @Override
  public boolean isSuccess(Response response) {
    if (response == null) {
      throw new IllegalArgumentException("Response is null");
    }
    return response.getStatus() == 200 || response.getStatus() == 204;
  }

}