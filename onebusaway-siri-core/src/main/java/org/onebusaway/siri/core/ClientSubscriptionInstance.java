package org.onebusaway.siri.core;

import uk.org.siri.siri.AbstractSubscriptionStructure;

/**
 * Captures information about an active client-to-server subscription.
 * 
 * @author bdferris
 * 
 */
class ClientSubscriptionInstance {

  private final ClientSubscriptionChannel channel;

  private final String subscriptionId;

  private ESiriModuleType moduleType;

  private AbstractSubscriptionStructure subscriptionRequest;

  public ClientSubscriptionInstance(ClientSubscriptionChannel channel,
      String subscriptionId, ESiriModuleType moduleType,
      AbstractSubscriptionStructure subscriptionRequest) {
    this.channel = channel;
    this.subscriptionId = subscriptionId;
    this.moduleType = moduleType;
    this.subscriptionRequest = subscriptionRequest;
  }

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public void setModuleType(ESiriModuleType moduleType) {
    this.moduleType = moduleType;
  }

  public AbstractSubscriptionStructure getSubscriptionRequest() {
    return subscriptionRequest;
  }

  public void setSubscriptionRequest(
      AbstractSubscriptionStructure subscriptionRequest) {
    this.subscriptionRequest = subscriptionRequest;
  }

  public ClientSubscriptionChannel getChannel() {
    return channel;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }
}