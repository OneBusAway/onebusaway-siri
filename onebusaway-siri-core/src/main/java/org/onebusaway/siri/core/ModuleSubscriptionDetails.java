package org.onebusaway.siri.core;

import java.util.List;

import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;

import uk.org.siri.siri.AbstractSubscriptionStructure;

class ModuleSubscriptionDetails {

  private final SubscriptionDetails details;

  private final ESiriModuleType moduleType;

  private final SubscriptionId id;

  private final AbstractSubscriptionStructure moduleSubscription;

  private final List<SiriModuleDeliveryFilter> filters;

  public ModuleSubscriptionDetails(SubscriptionDetails details,
      ESiriModuleType moduleType, SubscriptionId id,
      AbstractSubscriptionStructure moduleSubscription,
      List<SiriModuleDeliveryFilter> filters) {
    this.details = details;
    this.moduleType = moduleType;
    this.id = id;
    this.moduleSubscription = moduleSubscription;
    this.filters = filters;
  }

  public SubscriptionDetails getDetails() {
    return details;
  }

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public SubscriptionId getId() {
    return id;
  }

  public AbstractSubscriptionStructure getModuleSubscription() {
    return moduleSubscription;
  }

  public List<SiriModuleDeliveryFilter> getFilters() {
    return filters;
  }
}