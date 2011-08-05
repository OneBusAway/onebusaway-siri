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
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

class TerminateSubscriptionsManager extends AbstractManager {

  private static final Logger _log = LoggerFactory.getLogger(TerminateSubscriptionsManager.class);

  /**
   * This map contains pending subscription termination information by message
   * id
   */
  private ConcurrentMap<String, PendingTermination> _pendingSubscriptionTerminations = new ConcurrentHashMap<String, PendingTermination>();

  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  public void setSupport(ClientSupport support) {
    _support = support;
  }

  public void handleTerminateSubscriptionResponse(
      TerminateSubscriptionResponseStructure response) {

    MessageQualifierStructure messageIdRef = response.getRequestMessageRef();
    if (messageIdRef == null || messageIdRef.getValue() == null) {
      _support.logTerminateSubscriptionResponseWithoutRequestMessageRef(response);
      return;
    }

    String messageId = messageIdRef.getValue();

    PendingTermination pending = _pendingSubscriptionTerminations.remove(messageId);

    if (pending == null) {
      _support.logUnknownTerminateSubscriptionResponse(response);
      return;
    }

    ScheduledFuture<?> timeoutTask = pending.getTimeoutTask();
    timeoutTask.cancel(false);
    
    String subscriberId = pending.getSubscriberId();

    for (TerminationResponseStatus status : response.getTerminationResponseStatus()) {

      /**
       * We need to figure out which subscription this 'status' message belongs
       * to, but it's tricky, because things can be optional.
       */
      
      SubscriptionId id = _support.getSubscriptionIdForTerminationStatusResponse(status,subscriberId);

      if (status.isStatus()) {

        /**
         * Cancel the timeout task
         */

        _subscriptionManager.removeSubscription(id);

      } else {
        _support.logErrorInTerminateSubscriptionResponse(response, status, id);
      }

    }

    /**
     * If a re-subscription has been requested, we just send the original
     * subscription request
     */
    if (pending.isResubscribe()) {
      for (SiriClientRequest request : pending.getSubscriptionRequests())
        _client.handleRequest(request);
    }
  }

  public void terminateSubscription(ClientSubscriptionInstance instance,
      boolean resubscribeAfterTermination) {
    terminateSubscriptions(Arrays.asList(instance), resubscribeAfterTermination);
  }

  public List<String> terminateSubscriptions(
      Collection<ClientSubscriptionInstance> instances,
      boolean resubscribeAfterTermination) {

    List<String> pendingTerminationMessageIds = new ArrayList<String>();

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

        MessageQualifierStructure messageId = SiriTypeFactory.randomMessageId();

        SiriClientRequest request = _support.getTerminateSubscriptionRequestForSubscriptions(
            channel, messageId, subscriberId, subscriberInstances);

        List<SiriClientRequest> originalSubscriptionRequests = new ArrayList<SiriClientRequest>();

        for (ClientSubscriptionInstance instance : subscriberInstances)
          originalSubscriptionRequests.add(instance.getRequest());

        PendingSubscriptionTerminationTimeoutTask timeoutTask = new PendingSubscriptionTerminationTimeoutTask(
            messageId.getValue());
        ScheduledFuture<?> scheduled = _subscriptionManager.scheduleResponseTimeoutTask(timeoutTask);

        PendingTermination pending = new PendingTermination(scheduled,subscriberId,
            resubscribeAfterTermination, originalSubscriptionRequests);

        _pendingSubscriptionTerminations.put(messageId.getValue(), pending);
        pendingTerminationMessageIds.add(messageId.getValue());

        _client.handleRequest(request);
      }
    }

    return pendingTerminationMessageIds;
  }

  public void waitForPendingSubscriptionTerminationResponses(
      List<String> messageIds, int responseTimeout) {

    // We add a little buffer (500ms) just in case
    long waitUntil = System.currentTimeMillis() + responseTimeout * 1000 + 500;

    /**
     * TODO : Is there a better way to wait on a group of tasks?
     */
    for (String id : messageIds) {

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

    private final String _messageId;

    public PendingSubscriptionTerminationTimeoutTask(String messageId) {
      _messageId = messageId;
    }

    @Override
    public void run() {

      PendingTermination pending = _pendingSubscriptionTerminations.remove(_messageId);

      if (pending != null) {
        _log.warn("pending subscription termination expired before receiving a subscription termination response from server: "
            + _messageId);

        /**
         * Even if the termination failed, attempt to resubscribe if it's been
         * requested.
         */
        if (pending.isResubscribe()) {
          for (SiriClientRequest request : pending.getSubscriptionRequests())
            _client.handleRequest(request);
        }
      }
    }
  }

  private static class PendingTermination {

    private final ScheduledFuture<?> _timeoutTask;
    
    private final String _subscriberId;

    private final boolean _resubscribe;

    private final List<SiriClientRequest> _subscriptionRequests;

    public PendingTermination(ScheduledFuture<?> timeoutTask,
        String subscriberId, boolean resubscribe, List<SiriClientRequest> subscriptionRequests) {
      _timeoutTask = timeoutTask;
      _subscriberId = subscriberId;
      _resubscribe = resubscribe;
      _subscriptionRequests = subscriptionRequests;
    }

    public ScheduledFuture<?> getTimeoutTask() {
      return _timeoutTask;
    }
    
    public String getSubscriberId() {
      return _subscriberId;
    }

    public boolean isResubscribe() {
      return _resubscribe;
    }

    public List<SiriClientRequest> getSubscriptionRequests() {
      return _subscriptionRequests;
    }
  }
}
