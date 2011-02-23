package org.onebusaway.siri.core;

import uk.org.siri.siri.ServiceDelivery;

class SubscriptionEvent {

  private final ModuleSubscriptionDetails details;

  private final ServiceDelivery delivery;

  public SubscriptionEvent(ModuleSubscriptionDetails details,
      ServiceDelivery delivery) {
    this.details = details;
    this.delivery = delivery;
  }

  public ModuleSubscriptionDetails getDetails() {
    return details;
  }

  public ServiceDelivery getDelivery() {
    return delivery;
  }
}
