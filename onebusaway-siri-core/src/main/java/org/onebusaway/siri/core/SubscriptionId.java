package org.onebusaway.siri.core;

public class SubscriptionId {

  private final String subscriberId;

  private final String subscriptionId;

  public SubscriptionId(String subscriberId, String subscriptionId) {
    this.subscriberId = subscriberId;
    this.subscriptionId = subscriptionId;
  }

  public String getSubscriberId() {
    return subscriberId;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }
}
