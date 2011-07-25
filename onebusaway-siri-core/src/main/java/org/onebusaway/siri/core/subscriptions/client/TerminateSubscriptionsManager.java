package org.onebusaway.siri.core.subscriptions.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

class TerminateSubscriptionsManager extends AbstractManager {

  private static final Logger _log = LoggerFactory.getLogger(TerminateSubscriptionsManager.class);

  /**
   * This map contains pending subscription termination information
   */
  private ConcurrentMap<SubscriptionId, PendingTermination> _pendingSubscriptionTerminations = new ConcurrentHashMap<SubscriptionId, PendingTermination>();

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

      PendingTermination pending = _pendingSubscriptionTerminations.remove(id);

      if (pending == null) {
        _support.logUnknownTerminateSubscriptionResponse(response, id);
        continue;
      }

      if (status.isStatus()) {

        /**
         * Cancel the timeout task
         */
        ScheduledFuture<?> timeoutTask = pending.getTimeoutTask();
        timeoutTask.cancel(false);

        _subscriptionManager.removeSubscription(id);

      } else {
        _support.logErrorInTerminateSubscriptionResponse(response, status, id);
      }

      /**
       * If a re-subscription has been requested, we just send the original
       * subscription request
       */
      if (pending.isResubscribe())
        _client.handleRequest(pending.getSubscriptionRequest());
    }
  }

  public void terminateSubscription(ClientSubscriptionInstance instance,
      boolean resubscribeAfterTermination) {
    terminateSubscriptions(Arrays.asList(instance), resubscribeAfterTermination);
  }

  public List<SubscriptionId> terminateSubscriptions(
      Collection<ClientSubscriptionInstance> instances,
      boolean resubscribeAfterTermination) {

    List<SubscriptionId> pendingTermination = new ArrayList<SubscriptionId>();

    /**
     * First group active subscriptions by channel
     */
    Map<ClientSubscriptionChannel, List<ClientSubscriptionInstance>> instancesByChannel = MappingLibrary.mapToValueList(
        instances, "channel");

    for (Map.Entry<ClientSubscriptionChannel, List<ClientSubscriptionInstance>> channelEntry : instancesByChannel.entrySet()) {

      ClientSubscriptionChannel channel = channelEntry.getKey();
      List<ClientSubscriptionInstance> channelInstances = channelEntry.getValue();

      /**
       * Next, group active subscriptions by subscriber id so we can group the
       * subscription termination messages
       */
      Map<String, List<ClientSubscriptionInstance>> instancesBySubscriber = MappingLibrary.mapToValueList(
          channelInstances, "subscriptionId.subscriberId");

      for (Map.Entry<String, List<ClientSubscriptionInstance>> entry : instancesBySubscriber.entrySet()) {

        String subscriberId = entry.getKey();
        List<ClientSubscriptionInstance> subscriberInstances = entry.getValue();

        /**
         * TODO : Refactor this into terminate subscriptions manager?
         */
        SiriClientRequest request = _support.getTerminateSubscriptionRequestForSubscriptions(
            channel, subscriberId, subscriberInstances);

        /**
         * TODO: Thread-safety
         */

        for (ClientSubscriptionInstance instance : subscriberInstances) {

          SubscriptionId id = instance.getSubscriptionId();
          pendingTermination.add(id);

          PendingSubscriptionTerminationTimeoutTask timeoutTask = new PendingSubscriptionTerminationTimeoutTask(
              id);
          ScheduledFuture<?> scheduled = _subscriptionManager.scheduleResponseTimeoutTask(timeoutTask);

          PendingTermination pending = new PendingTermination(scheduled,
              resubscribeAfterTermination, instance.getRequest());

          PendingTermination existing = _pendingSubscriptionTerminations.put(
              id, pending);

          if (existing != null) {
            _log.warn("overwriting existing pending subscription termination request: "
                + id);
            ScheduledFuture<?> existingTask = existing.getTimeoutTask();
            existingTask.cancel(false);
          }
        }

        _client.handleRequest(request);
      }
    }

    return pendingTermination;
  }

  public void waitForPendingSubscriptionTerminationResponses(
      List<SubscriptionId> subscriptionIds, int responseTimeout) {

    // We add a little buffer (500ms) just in case
    long waitUntil = System.currentTimeMillis() + responseTimeout * 1000 + 500;

    /**
     * TODO : Is there a better way to wait on a group of tasks?
     */
    for (SubscriptionId id : subscriptionIds) {

      PendingTermination pending = _pendingSubscriptionTerminations.get(id);

      if (pending == null)
        continue;

      long remaining = waitUntil - System.currentTimeMillis();
      if (remaining <= 0)
        break;

      try {
        ScheduledFuture<?> task = pending.getTimeoutTask();
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

      PendingTermination pending = _pendingSubscriptionTerminations.remove(_subscriptionId);
      if (pending != null) {
        _log.warn("pending subscription termination expired before receiving a subscription termination response from server: "
            + _subscriptionId);

        /**
         * Even if the termination failed, attempt to resubscribe if it's been
         * requested.
         */
        if (pending.isResubscribe())
          _client.handleRequest(pending.getSubscriptionRequest());
      }
    }
  }

  private static class PendingTermination {

    private final ScheduledFuture<?> _timeoutTask;

    private final boolean _resubscribe;

    private final SiriClientRequest _subscriptionRequest;

    public PendingTermination(ScheduledFuture<?> timeoutTask,
        boolean resubscribe, SiriClientRequest subscriptionRequest) {
      _timeoutTask = timeoutTask;
      _resubscribe = resubscribe;
      _subscriptionRequest = subscriptionRequest;
    }

    public ScheduledFuture<?> getTimeoutTask() {
      return _timeoutTask;
    }

    public boolean isResubscribe() {
      return _resubscribe;
    }

    public SiriClientRequest getSubscriptionRequest() {
      return _subscriptionRequest;
    }
  }
}
