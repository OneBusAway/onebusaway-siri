package org.onebusaway.siri.core;

import uk.org.siri.siri.ServiceDelivery;

class SubscriptionEvent {

  private final SubscriptionDetails details;

  private final ServiceDelivery delivery;

  public SubscriptionEvent(SubscriptionDetails details,
      ServiceDelivery delivery) {
    this.details = details;
    this.delivery = delivery;
  }

  public SubscriptionDetails getDetails() {
    return details;
  }

  public ServiceDelivery getDelivery() {
    return delivery;
  }
}
