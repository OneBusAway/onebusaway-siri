package org.onebusaway.siri.core;

import uk.org.siri.siri.SubscriptionRequest;

public class SiriClientSubscriptionRequest extends
    AbstractSiriClientRequest<SubscriptionRequest> {

  public SiriClientSubscriptionRequest() {

  }

  public SiriClientSubscriptionRequest(AbstractSiriClientRequest<?> request,
      SubscriptionRequest subscriptionRequest) {
    super(request);
    setPayload(subscriptionRequest);
  }
}
