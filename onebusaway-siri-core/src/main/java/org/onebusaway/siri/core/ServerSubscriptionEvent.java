package org.onebusaway.siri.core;

import uk.org.siri.siri.ServiceDelivery;

class ServerSubscriptionEvent {

  private final ServerSubscriptionInstance instance;

  private final ServiceDelivery delivery;

  public ServerSubscriptionEvent(ServerSubscriptionInstance instance,
      ServiceDelivery delivery) {
    this.instance = instance;
    this.delivery = delivery;
  }

  public ServerSubscriptionInstance getInstance() {
    return instance;
  }

  public ServiceDelivery getDelivery() {
    return delivery;
  }
}
