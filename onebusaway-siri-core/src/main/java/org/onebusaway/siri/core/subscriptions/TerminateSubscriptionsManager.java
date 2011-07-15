package org.onebusaway.siri.core.subscriptions;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

class TerminateSubscriptionsManager extends AbstractManager {

  private static final Logger _log = LoggerFactory.getLogger(TerminateSubscriptionsManager.class);

  /**
   * This map contains pending subscription termination timeout tasks
   */
  private ConcurrentMap<SubscriptionId, ScheduledFuture<?>> _pendingSubscriptionTerminationTasks = new ConcurrentHashMap<SubscriptionId, ScheduledFuture<?>>();

  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  public void setSupport(ClientSupport support) {
    _support = support;
  }

  public void handleTerminateSubscriptionResponse(
      TerminateSubscriptionResponseStructure response) {

    for (TerminationResponseStatus status : response.getTerminationResponseStatus()) {

      SubscriptionId id = _support.getSubscriptionIdForTerminationStatusResponse(status);

      ScheduledFuture<?> pending = _pendingSubscriptionTerminationTasks.remove(id);

      if (pending == null) {
        _support.logUnknownTerminateSubscriptionResponse(response, id);
        continue;
      }

      if (status.isStatus()) {

        /**
         * Cancel the timeout task
         */
        pending.cancel(false);

        _subscriptionManager.terminateSubscription(id);

      } else {
        _support.logErrorInTerminateSubscriptionResponse(response, status, id);
      }
    }
  }

  public void terminateSubscription(ClientSubscriptionInstance instance) {

    SubscriptionId id = instance.getSubscriptionId();

    PendingSubscriptionTerminationTimeoutTask timeoutTask = new PendingSubscriptionTerminationTimeoutTask(
        id);
    ScheduledFuture<?> scheduled = _subscriptionManager.scheduleResponseTimeoutTask(timeoutTask);

    ScheduledFuture<?> existing = _pendingSubscriptionTerminationTasks.put(id,
        scheduled);

    if (existing != null) {
      _log.warn("overwriting existing pending subscription termination request: "
          + id);
      existing.cancel(false);
    }
  }

  public void waitForPendingSubscriptionTerminationResponses(
      List<SubscriptionId> subscriptionIds, int responseTimeout) {

    // We add a little buffer (500ms) just in case
    long waitUntil = System.currentTimeMillis() + responseTimeout * 1000 + 500;

    /**
     * TODO : Is there a better way to wait on a group of tasks?
     */
    for (SubscriptionId id : subscriptionIds) {

      ScheduledFuture<?> task = _pendingSubscriptionTerminationTasks.get(id);

      if (task == null)
        continue;

      long remaining = waitUntil - System.currentTimeMillis();
      if (remaining <= 0)
        break;

      try {
        task.get(remaining, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
        break;
      }
    }
  }

  /****
   * Private Classes
   ****/

  private class PendingSubscriptionTerminationTimeoutTask implements Runnable {

    private final SubscriptionId _subscriptionId;

    public PendingSubscriptionTerminationTimeoutTask(
        SubscriptionId subscriptionId) {
      _subscriptionId = subscriptionId;
    }

    @Override
    public void run() {

      ScheduledFuture<?> pending = _pendingSubscriptionTerminationTasks.remove(_subscriptionId);
      if (pending != null) {
        _log.warn("pending subscription termination expired before receiving a subscription termination response from server: "
            + _subscriptionId);
      }
    }
  }
}
