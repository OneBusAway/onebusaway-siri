package org.onebusaway.siri.core.subscriptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionExpirationTask implements Runnable {

  private static final Logger _log = LoggerFactory.getLogger(SubscriptionExpirationTask.class);

  private final SiriClientSubscriptionManager _manager;

  private final SubscriptionId _id;

  public SubscriptionExpirationTask(SiriClientSubscriptionManager manager,
      SubscriptionId id) {
    _manager = manager;
    _id = id;
  }

  @Override
  public void run() {
    _log.debug("expiring subscription " + _id);
    _manager.handleUnsubscribeAndResubscribe(_id);
  }
}
