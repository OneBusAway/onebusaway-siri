package org.onebusaway.siri.core;

import uk.org.siri.siri.SubscriptionRequest;

public class SiriClientSubscriptionRequest extends AbstractSiriClientRequest {

  private SubscriptionRequest request;

  private int heartbeatInterval = 0;

  public SubscriptionRequest getRequest() {
    return request;
  }

  public void setRequest(SubscriptionRequest request) {
    this.request = request;
  }

  public int getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(int heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }
}
