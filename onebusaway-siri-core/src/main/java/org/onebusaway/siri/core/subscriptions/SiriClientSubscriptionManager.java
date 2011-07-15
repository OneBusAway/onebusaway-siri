package org.onebusaway.siri.core.subscriptions;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.HeartbeatNotificationStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;

public class SiriClientSubscriptionManager {

  private static Logger _log = LoggerFactory.getLogger(SiriClientSubscriptionManager.class);

  private ConcurrentMap<String, ClientSubscriptionChannel> _channelsByAddress = new ConcurrentHashMap<String, ClientSubscriptionChannel>();

  /**
   * This contains active subscriptions, indexed by subscription id. Note that
   * we use a ConcurrentMap here, so that we can safely access values in the map
   * concurrently, since not all the methods accessing the map are synchronized.
   */
  private ConcurrentMap<SubscriptionId, ClientSubscriptionInstance> _activeSubscriptions = new ConcurrentHashMap<SubscriptionId, ClientSubscriptionInstance>();

  private ScheduledExecutorService _executor;

  private SiriClientHandler _client;

  private ClientSupport _support = new ClientSupport();

  private InitiateSubscriptionsManager _initiateSubscriptionsManager = new InitiateSubscriptionsManager();

  private CheckStatusManager _checkStatusManager = new CheckStatusManager();

  private TerminateSubscriptionsManager _terminateSubscriptionsManager = new TerminateSubscriptionsManager();

  /**
   * Timeout, in seconds, in which we expect to receive a response for a pending
   * request if it's received asynchronously (ex SubscriptionRequest =>
   * SubscriptionResponse).
   */
  private int _responseTimeout = 10;

  /**
   * Timeout, in seconds, in which we expect to receive a response for a pending
   * request if it's received asynchronously (ex SubscriptionRequest =>
   * SubscriptionResponse).
   * 
   * @param responseTimeout timeout, in seconds
   */
  public void setResponseTimeout(int responseTimeout) {
    _responseTimeout = responseTimeout;
  }

  /**
   * Timeout, in seconds, in which we expect to receive a response for a pending
   * request if it's received asynchronously (ex SubscriptionRequest =>
   * SubscriptionResponse).
   */
  public int getResponseTimeout() {
    return _responseTimeout;
  }

  public void setSiriClientHandler(SiriClientHandler client) {
    _client = client;
  }

  /****
   * Client Start and Stop Methods
   ****/

  /**
   * 
   */
  public void start() {

    _log.debug("starting client subscription manager");

    _executor = Executors.newSingleThreadScheduledExecutor();

    wireManager(_initiateSubscriptionsManager);
    wireManager(_checkStatusManager);
    wireManager(_terminateSubscriptionsManager);
  }

  /**
   * 
   */
  public void stop() {

    _log.debug("stopping client subscription manager");

    if (_executor != null)
      _executor.shutdownNow();
  }

  /**
   * Register a pending subscription request. We don't actually consider a
   * subscription active until we receive a <SubscriptionResponse/> message,
   * which may arrive asynchronously or perhaps not at all.
   * 
   * @param request
   * @param subscriptionRequest
   * @return true if the pending subscriptions were properly registered,
   *         otherwise false
   */
  public boolean registerPendingSubscription(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    return _initiateSubscriptionsManager.registerPendingSubscription(request,
        subscriptionRequest);
  }

  /**
   * Handle an incoming subscription response message, matching the response to
   * a pending subscription request, and upgrading the subscription to active
   * status, assuming there are no errors.
   * 
   * @param response
   */
  public void handleSubscriptionResponse(SubscriptionResponseStructure response) {

    _initiateSubscriptionsManager.handleSubscriptionResponse(response);

  }

  public boolean isSubscriptionActiveForModuleDelivery(
      AbstractServiceDeliveryStructure moduleDelivery) {

    SubscriptionId id = _support.getSubscriptionIdForModuleDelivery(moduleDelivery);
    return _activeSubscriptions.containsKey(id);
  }

  public SiriChannelInfo getChannelInfoForServiceDelivery(
      ServiceDelivery serviceDelivery) {

    SiriChannelInfo channelInfo = new SiriChannelInfo();

    ClientSubscriptionChannel clientSubscriptionChannel = null;

    String address = serviceDelivery.getAddress();
    if (address != null)
      clientSubscriptionChannel = _channelsByAddress.get(address);

    ParticipantRefStructure producerRef = serviceDelivery.getProducerRef();
    if (producerRef != null && producerRef.getValue() != null) {
      ClientSubscriptionChannel other = _channelsByAddress.get(producerRef.getValue());
      if (other != null)
        clientSubscriptionChannel = other;
    }

    if (clientSubscriptionChannel != null) {
      channelInfo.setContext(clientSubscriptionChannel.getContext());
    }

    return channelInfo;
  }

  public void handleTerminateSubscriptionResponse(
      TerminateSubscriptionResponseStructure response) {
    _terminateSubscriptionsManager.handleTerminateSubscriptionResponse(response);
  }

  public void terminateAllSubscriptions(
      boolean waitForTerminateSubscriptionResponseOnExit) {

    List<SubscriptionId> allPendings = _terminateSubscriptionsManager.terminateSubscriptions(
        _activeSubscriptions.values(), false);

    if (waitForTerminateSubscriptionResponseOnExit)
      _terminateSubscriptionsManager.waitForPendingSubscriptionTerminationResponses(
          allPendings, _responseTimeout);
  }

  public void handleCheckStatusNotification(
      CheckStatusResponseStructure response) {

    /**
     * Pass the response on to the CheckStatus manager
     */
    _checkStatusManager.handleCheckStatusResponse(response);
  }

  public void handleHeartbeatNotification(
      HeartbeatNotificationStructure heartbeat) {

    _log.debug("hearbeat notification");

    ClientSubscriptionChannel channel = null;

    ParticipantRefStructure producerRef = heartbeat.getProducerRef();
    if (producerRef != null && producerRef.getValue() != null) {
      channel = _channelsByAddress.get(producerRef.getValue());
    }

    if (channel == null && heartbeat.getAddress() != null)
      channel = _channelsByAddress.get(heartbeat.getAddress());

    if (channel != null) {
      synchronized (channel) {
        resetHeartbeat(channel, channel.getHeartbeatInterval());
      }
    }
  }

  /****
   * Package Methods
   ****/

  ScheduledFuture<?> scheduleResponseTimeoutTask(Runnable timeoutTask) {
    return _executor.schedule(timeoutTask, _responseTimeout, TimeUnit.SECONDS);
  }

  ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
      long period, TimeUnit unit) {
    return _executor.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  void upgradePendingSubscription(SubscriptionResponseStructure response,
      StatusResponseStructure status, SubscriptionId subId,
      ClientPendingSubscription pending) {

    SiriClientRequest request = pending.getRequest();

    ClientSubscriptionChannel channel = getChannelForServer(
        request.getTargetUrl(), request.getTargetVersion());

    Date validUntil = request.getInitialTerminationTime();

    if (validUntil == null)
      throw new SiriException("expected an initial termination time");

    if (status.getValidUntil() != null)
      validUntil = status.getValidUntil();

    _log.debug("subscription is valid until {}", validUntil);

    ScheduledFuture<?> expiration = null;

    long delay = validUntil.getTime() - System.currentTimeMillis();
    if (delay > 0) {
      SubscriptionExpirationTask task = new SubscriptionExpirationTask(this,
          subId);
      expiration = _executor.schedule(task, delay, TimeUnit.MILLISECONDS);
    } else {
      _log.warn(
          "subscription has already expired before it had a chance to start: id={} validUntil={}",
          subId, validUntil);
    }

    ClientSubscriptionInstance instance = new ClientSubscriptionInstance(
        channel, subId, request, pending.getModuleType(),
        pending.getModuleRequest(), expiration);

    // TODO: Thread safety
    ClientSubscriptionInstance existing = _activeSubscriptions.put(subId,
        instance);

    if (existing != null) {
      _log.info("overwriting existing subscription: " + subId);
      // TODO : Cleanup existing instance?
    }

    // TODO: Thread safety
    Set<SubscriptionId> channelSubscriptions = channel.getSubscriptions();
    channelSubscriptions.add(subId);

    updateChannelWithClientRequest(channel, request, response);
  }

  ESiriModuleType getModuleTypeForSubscriptionId(SubscriptionId subId) {
    ClientSubscriptionInstance instance = _activeSubscriptions.get(subId);
    if (instance == null)
      return null;
    return instance.getModuleType();
  }

  void removeSubscription(SubscriptionId id) {

    /**
     * TODO: Thread safety
     */
    ClientSubscriptionInstance instance = _activeSubscriptions.remove(id);

    if (instance == null) {
      _log.warn("subscription has already been removed: {}", id);
      return;
    }

    ScheduledFuture<?> expirationTask = instance.getExpirationTask();
    if (expirationTask != null)
      expirationTask.cancel(true);

    ClientSubscriptionChannel channel = instance.getChannel();
    Set<SubscriptionId> subscriptions = channel.getSubscriptions();
    subscriptions.remove(instance);
    if (subscriptions.isEmpty()) {

      _channelsByAddress.remove(channel.getAddress());

      ScheduledFuture<?> checkStatusTask = channel.getCheckStatusTask();
      if (checkStatusTask != null)
        checkStatusTask.cancel(true);

      ScheduledFuture<?> heartbeatTask = channel.getHeartbeatTask();
      if (heartbeatTask != null)
        heartbeatTask.cancel(true);
    }

  }

  void handleDisconnectAndReconnect(ClientSubscriptionChannel channel) {

    _log.info("terminate subscription: {}", channel);
    _channelsByAddress.remove(channel.getAddress());

    synchronized (channel) {
      resetHeartbeat(channel, 0);
      _checkStatusManager.resetCheckStatusTask(channel, 0);
    }

    SubscriptionRequest request = new SubscriptionRequest();

    Set<SubscriptionId> subscriptionIds = channel.getSubscriptions();

    for (SubscriptionId subscriptionId : subscriptionIds) {

      // TODO : Thread safety
      ClientSubscriptionInstance instance = _activeSubscriptions.get(subscriptionId);

      if (instance != null) {
        ESiriModuleType moduleType = instance.getModuleType();
        AbstractSubscriptionStructure moduleRequest = instance.getModuleRequest();

        List<AbstractSubscriptionStructure> subRequests = SiriLibrary.getSubscriptionRequestsForModule(
            request, moduleType);
        subRequests.add(moduleRequest);
      }
    }

    SiriClientRequest clientRequest = new SiriClientRequest();
    clientRequest.setCheckStatusInterval((int) channel.getCheckStatusInterval());
    clientRequest.setHeartbeatInterval((int) channel.getHeartbeatInterval());
    clientRequest.setReconnectionInterval(channel.getReconnectionInterval());
    clientRequest.setReconnectionAttempts(channel.getReconnectionAttempts());
    clientRequest.setTargetUrl(channel.getAddress());
    clientRequest.setTargetVersion(channel.getTargetVersion());
    clientRequest.setChannelContext(channel.getContext());

    Siri payload = new Siri();
    payload.setSubscriptionRequest(request);

    clientRequest.setPayload(payload);

    _client.handleRequest(clientRequest);
  }

  void handleUnsubscribeAndResubscribe(SubscriptionId id) {

    ClientSubscriptionInstance instance = _activeSubscriptions.get(id);

    if (instance == null) {
      _log.warn("no subscription found to unsubscribe/resubscribe with id={}",
          id);
      return;
    }

    /**
     * Terminate the subscription, indicating that we want to re-subscribe
     */
    _terminateSubscriptionsManager.terminateSubscription(instance, true);
  }

  /****
   * Private Methods
   ****/

  private void wireManager(AbstractManager manager) {
    /**
     * TODO: Maybe it's time for a IOC Dependency Injection library?
     */
    manager.setClient(_client);
    manager.setSubscriptionManager(this);
    manager.setSupport(_support);
  }

  /**
   * Here we register CheckStatus and Heartbeat tasks for a particular channel
   * if a client has requested it.
   * 
   * @param channel
   * @param request
   * @param response
   */
  private void updateChannelWithClientRequest(
      ClientSubscriptionChannel channel, SiriClientRequest request,
      SubscriptionResponseStructure response) {

    synchronized (channel) {

      Date serviceStartedTime = response.getServiceStartedTime();
      if (serviceStartedTime != null)
        channel.setLastServiceStartedTime(serviceStartedTime);

      channel.setReconnectionAttempts(request.getReconnectionAttempts());
      channel.setReconnectionInterval(request.getReconnectionInterval());
      channel.setContext(request.getChannelContext());

      long heartbeatInterval = request.getHeartbeatInterval();

      if (heartbeatInterval != channel.getHeartbeatInterval()) {
        channel.setHeartbeatInterval(heartbeatInterval);
        resetHeartbeat(channel, heartbeatInterval);
      }

      long checkStatusInterval = request.getCheckStatusInterval();

      if (checkStatusInterval > 0) {
        channel.setCheckStatusInterval(checkStatusInterval);
        _checkStatusManager.resetCheckStatusTask(channel, checkStatusInterval);
      }
    }
  }

  private void resetHeartbeat(ClientSubscriptionChannel channel,
      long heartbeatInterval) {

    ScheduledFuture<?> heartbeatTask = channel.getHeartbeatTask();
    if (heartbeatTask != null)
      heartbeatTask.cancel(true);

    if (heartbeatInterval > 0) {
      ClientHeartbeatTimeoutTask task = new ClientHeartbeatTimeoutTask(this,
          channel);

      // Why is this * 2? We want to give the heartbeat a little slack time.
      // Could be better...
      heartbeatTask = _executor.schedule(task, heartbeatInterval * 2,
          TimeUnit.SECONDS);
      channel.setHeartbeatTask(heartbeatTask);
    }
  }

  private ClientSubscriptionChannel getChannelForServer(String address,
      ESiriVersion targetVersion) {

    ClientSubscriptionChannel channel = _channelsByAddress.get(address);

    if (channel == null) {

      ClientSubscriptionChannel newChannel = new ClientSubscriptionChannel(
          address, targetVersion);

      channel = _channelsByAddress.putIfAbsent(address, newChannel);
      if (channel == null)
        channel = newChannel;
    }

    if (channel.getTargetVersion() != targetVersion) {
      throw new SiriException("existing channel with address " + address
          + " has SIRI version " + channel.getTargetVersion()
          + " but a new subscription wants to use version " + targetVersion);
    }

    return channel;
  }
}
