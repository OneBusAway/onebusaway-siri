package org.onebusaway.siri.core.subscriptions.server;

import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.ServiceDelivery;

public class SiriServerSubscriptionEvent {

  private final SubscriptionId subscriptionId;

  private final String address;

  private final ESiriVersion targetVersion;

  private final ServiceDelivery delivery;

  public SiriServerSubscriptionEvent(SubscriptionId subscriptionId, String address,
      ESiriVersion targetVersion, ServiceDelivery delivery) {
    this.subscriptionId = subscriptionId;
    this.address = address;
    this.targetVersion = targetVersion;
    this.delivery = delivery;
  }

  public SubscriptionId getSubscriptionId() {
    return subscriptionId;
  }

  public String getAddress() {
    return address;
  }

  public ESiriVersion getTargetVersion() {
    return targetVersion;
  }

  public ServiceDelivery getDelivery() {
    return delivery;
  }
}
