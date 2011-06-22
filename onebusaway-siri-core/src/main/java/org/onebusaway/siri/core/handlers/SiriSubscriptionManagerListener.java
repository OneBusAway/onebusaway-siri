package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.SiriSubscriptionManager;

public interface SiriSubscriptionManagerListener {

  public void subscriptionAdded(SiriSubscriptionManager manager);

  public void subscriptionRemoved(SiriSubscriptionManager manager);
}
