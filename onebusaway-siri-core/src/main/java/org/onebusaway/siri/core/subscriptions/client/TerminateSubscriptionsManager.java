package org.onebusaway.siri.core.subscriptions.client;

import static org.onebusaway.siri.core.subscriptions.client.ClientSupport.appendError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.siri.core.SchedulingService;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

@Singleton
class TerminateSubscriptionsManager {

  private static final Logger _log = LoggerFactory.getLogger(TerminateSubscriptionsManager.class);

  /**
   * This map contains pending subscription termination information by message
   * id
   */
  private ConcurrentMap<String, PendingTermination> _pendingSubscriptionTerminations = new ConcurrentHashMap<String, PendingTermination>();

  private SiriClientSubscriptionManager _subscriptionManager;

  private SiriClientHandler _client;

  private SchedulingService _schedulingService;

  @Inject
  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  @Inject
  public void setClient(SiriClientHandler client) {
    _client = client;
  }

  @Inject
  public void setScheduleService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  /****
   * 
   ****/

  public void requestTerminationOfSubscription(
      ClientSubscriptionInstance instance, boolean resubscribeAfterTermination) {
    requestTerminationOfSubscriptions(Arrays.asList(instance),
        resubscribeAfterTermination);
  }

  public List<String> requestTerminationOfSubscriptions(
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

        SiriClientRequest request = getTerminateSubscriptionRequestForSubscriptions(
            channel, messageId, subscriberId, subscriberInstances);

        List<SiriClientRequest> originalSubscriptionRequests = new ArrayList<SiriClientRequest>();

        for (ClientSubscriptionInstance instance : subscriberInstances)
          originalSubscriptionRequests.add(instance.getRequest());

        PendingSubscriptionTerminationTimeoutTask timeoutTask = new PendingSubscriptionTerminationTimeoutTask(
            messageId.getValue());
        ScheduledFuture<?> scheduled = _schedulingService.scheduleResponseTimeoutTask(timeoutTask);

        PendingTermination pending = new PendingTermination(scheduled,
            subscriberId, resubscribeAfterTermination,
            originalSubscriptionRequests);

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

  public void handleTerminateSubscriptionResponse(
      TerminateSubscriptionResponseStructure response) {

    MessageQualifierStructure messageIdRef = response.getRequestMessageRef();
    if (messageIdRef == null || messageIdRef.getValue() == null) {
      logTerminateSubscriptionResponseWithoutRequestMessageRef(response);
      return;
    }

    String messageId = messageIdRef.getValue();

    PendingTermination pending = _pendingSubscriptionTerminations.remove(messageId);

    if (pending == null) {
      logUnknownTerminateSubscriptionResponse(response);
      return;
    }

    /**
     * Cancel the waiting-for-response timeout task
     */
    ScheduledFuture<?> timeoutTask = pending.getTimeoutTask();
    timeoutTask.cancel(false);

    String subscriberId = pending.getSubscriberId();

    for (TerminationResponseStatus status : response.getTerminationResponseStatus()) {

      SubscriptionId id = getSubscriptionIdForTerminationStatusResponse(status,
          subscriberId);

      if (status.isStatus()) {

        _subscriptionManager.removeSubscription(id);

      } else {
        logErrorInTerminateSubscriptionResponse(response, status, id);
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

  /****
   * Private Methods
   ****/

  public SubscriptionId getSubscriptionIdForTerminationStatusResponse(
      TerminationResponseStatus status, String subscriberId) {

    ParticipantRefStructure subscriberRef = status.getSubscriberRef();
    SubscriptionQualifierStructure subscriptionRef = status.getSubscriptionRef();

    /**
     * TODO: If the subscriberRef has been specified directly, do we allow it to
     * override?
     */
    if (subscriberRef == null || subscriberRef.getValue() == null)
      subscriberRef = SiriTypeFactory.particpantRef(subscriberId);

    return ClientSupport.getSubscriptionId(subscriberRef, subscriptionRef);
  }

  public SiriClientRequest getTerminateSubscriptionRequestForSubscriptions(
      ClientSubscriptionChannel channel, MessageQualifierStructure messageId,
      String subscriberId,
      List<ClientSubscriptionInstance> subscriptionInstances) {

    TerminateSubscriptionRequestStructure terminateRequest = new TerminateSubscriptionRequestStructure();

    terminateRequest.setMessageIdentifier(messageId);

    ParticipantRefStructure subscriberRef = SiriTypeFactory.particpantRef(subscriberId);
    terminateRequest.setSubscriberRef(subscriberRef);

    for (ClientSubscriptionInstance instance : subscriptionInstances) {

      SubscriptionId id = instance.getSubscriptionId();

      SubscriptionQualifierStructure value = new SubscriptionQualifierStructure();
      value.setValue(id.getSubscriptionId());
      terminateRequest.getSubscriptionRef().add(value);
    }

    Siri payload = new Siri();
    payload.setTerminateSubscriptionRequest(terminateRequest);

    String url = channel.getManageSubscriptionUrl();
    if (url == null)
      url = channel.getAddress();

    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl(url);
    request.setTargetVersion(channel.getTargetVersion());
    request.setPayload(payload);
    return request;
  }

  public void logTerminateSubscriptionResponseWithoutRequestMessageRef(
      TerminateSubscriptionResponseStructure response) {
    StringBuilder b = new StringBuilder();
    b.append("A <TerminateSubscriptionResponse/> was received with no <RequestMessageRef/> value: ");
    if (response.getAddress() != null)
      b.append(" address=").append(response.getAddress());
    if (response.getResponderRef() != null
        && response.getResponderRef().getValue() != null)
      b.append(" responderRef=").append(response.getResponderRef().getValue());
    _log.warn(b.toString());
  }

  public void logUnknownTerminateSubscriptionResponse(
      TerminateSubscriptionResponseStructure response) {
    StringBuilder b = new StringBuilder();
    b.append("A <TerminateSubscriptionResponse/> was received with no pending <TerminateSubscriptionRequest/> having been sent:");
    if (response.getAddress() != null)
      b.append(" address=").append(response.getAddress());
    if (response.getResponderRef() != null
        && response.getResponderRef().getValue() != null)
      b.append(" responderRef=").append(response.getResponderRef().getValue());
    if (response.getRequestMessageRef() != null
        && response.getRequestMessageRef().getValue() != null)
      b.append(" requestMessageRef=").append(
          response.getRequestMessageRef().getValue());
    _log.warn(b.toString());
  }

  private void logErrorInTerminateSubscriptionResponse(
      TerminateSubscriptionResponseStructure response,
      TerminationResponseStatus status, SubscriptionId subId) {

    StringBuilder b = new StringBuilder();
    b.append("We received an error response for a subscription request:");
    if (response.getAddress() != null)
      b.append(" address=").append(response.getAddress());
    if (response.getResponderRef() != null
        && response.getResponderRef().getValue() != null)
      b.append(" responderRef=" + response.getResponderRef().getValue());
    b.append(" subscriptionId=" + subId);
    TerminationResponseStatus.ErrorCondition error = status.getErrorCondition();

    if (error != null) {
      appendError(error.getCapabilityNotSupportedError(), b);
      appendError(error.getUnknownSubscriberError(), b);
      appendError(error.getUnknownSubscriptionError(), b);
      appendError(error.getOtherError(), b);

      if (error.getDescription() != null
          && error.getDescription().getValue() != null)
        b.append(" errorDescription=").append(error.getDescription().getValue());
    }

    _log.warn(b.toString());
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
        String subscriberId, boolean resubscribe,
        List<SiriClientRequest> subscriptionRequests) {
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
