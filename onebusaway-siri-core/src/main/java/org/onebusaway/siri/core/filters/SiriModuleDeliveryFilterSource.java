package org.onebusaway.siri.core.filters;

import java.util.List;

import org.onebusaway.siri.core.ESiriModuleType;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.SubscriptionRequest;

public interface SiriModuleDeliveryFilterSource {

  public void addFiltersForModuleSubscription(
      SubscriptionRequest subscriptionRequest, ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleTypeSubscriptionRequest,
      List<SiriModuleDeliveryFilter> filters);
}
