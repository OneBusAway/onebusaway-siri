package org.onebusaway.siri.core.subscriptions;

import java.util.concurrent.ScheduledFuture;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriClientRequest;

import uk.org.siri.siri.AbstractSubscriptionStructure;

/**
 * Captures information about an active client-to-server subscription.
 * 
 * @author bdferris
 * 
 */
class ClientSubscriptionInstance {

  private final ClientSubscriptionChannel channel;

  private final SubscriptionId subscriptionId;

  private final SiriClientRequest request;

  private final ESiriModuleType moduleType;

  private final AbstractSubscriptionStructure moduleRequest;

  private final ScheduledFuture<?> expirationTask;

  public ClientSubscriptionInstance(ClientSubscriptionChannel channel,
      SubscriptionId subscriptionId, SiriClientRequest request,
      ESiriModuleType moduleType, AbstractSubscriptionStructure moduleRequest,
      ScheduledFuture<?> expirationTask) {
    this.channel = channel;
    this.subscriptionId = subscriptionId;
    this.request = request;
    this.moduleType = moduleType;
    this.moduleRequest = moduleRequest;
    this.expirationTask = expirationTask;
  }

  public ClientSubscriptionChannel getChannel() {
    return channel;
  }

  public SubscriptionId getSubscriptionId() {
    return subscriptionId;
  }

  public SiriClientRequest getRequest() {
    return request;
  }

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public AbstractSubscriptionStructure getModuleRequest() {
    return moduleRequest;
  }

  public ScheduledFuture<?> getExpirationTask() {
    return expirationTask;
  }
}