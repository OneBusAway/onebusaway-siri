package org.onebusaway.siri.core;

import java.util.List;

import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.SubscriptionRequest;

class SubscriptionDetails {

  private final ESiriModuleType moduleType;

  private final SubscriptionId id;

  private final String address;
  
  private final SubscriptionRequest subscriptionRequest;

  private final AbstractSubscriptionStructure moduleSubscription;

  private final List<SiriModuleDeliveryFilter> filters;

  public SubscriptionDetails(ESiriModuleType moduleType, SubscriptionId id,
      String address, SubscriptionRequest subscriptionRequest, AbstractSubscriptionStructure moduleSubscription,
      List<SiriModuleDeliveryFilter> filters) {
    this.moduleType = moduleType;
    this.id = id;
    this.address = address;
    this.subscriptionRequest = subscriptionRequest;
    this.moduleSubscription = moduleSubscription;
    this.filters = filters;
  }

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public SubscriptionId getId() {
    return id;
  }

  public String getAddress() {
    return address;
  }
  
  public SubscriptionRequest getSubscriptionRequest() {
    return subscriptionRequest;
  }

  public AbstractSubscriptionStructure getModuleSubscription() {
    return moduleSubscription;
  }

  public List<SiriModuleDeliveryFilter> getFilters() {
    return filters;
  }
}