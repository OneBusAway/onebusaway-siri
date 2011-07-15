package org.onebusaway.siri.core.subscriptions;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriClientRequest;

import uk.org.siri.siri.AbstractSubscriptionStructure;

class ClientPendingSubscription {

  private final SubscriptionId id;

  private final SiriClientRequest request;

  private final ESiriModuleType moduleType;

  private final AbstractSubscriptionStructure moduleRequest;

  public ClientPendingSubscription(SubscriptionId id,
      SiriClientRequest request, ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleRequest) {
    this.id = id;
    this.request = request;
    this.moduleType = moduleType;
    this.moduleRequest = moduleRequest;
  }

  public SubscriptionId getId() {
    return id;
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
}
