package org.onebusaway.siri.core.filters;

import org.onebusaway.siri.core.ESiriModuleType;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.SubscriptionRequest;

public interface SiriModuleDeliveryFilterMatcher {

  public boolean isMatch(SubscriptionRequest subscriptionRequest,
      ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleTypeSubscriptionRequest);
}
