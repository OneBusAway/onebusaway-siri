package org.onebusaway.siri.core;

import uk.org.siri.siri.SubscriptionRequest;

public class SiriClientSubscriptionRequest extends AbstractSiriClientRequest {

  private SubscriptionRequest request;

  public SubscriptionRequest getRequest() {
    return request;
  }

  public void setRequest(SubscriptionRequest request) {
    this.request = request;
  }
}
