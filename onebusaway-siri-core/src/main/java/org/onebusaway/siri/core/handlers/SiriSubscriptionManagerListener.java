package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionManager;

public interface SiriSubscriptionManagerListener {

  public void subscriptionAdded(SiriServerSubscriptionManager manager);

  public void subscriptionRemoved(SiriServerSubscriptionManager manager);
}
