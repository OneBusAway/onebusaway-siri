/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.core.subscriptions.client;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SchedulingService;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.HeartbeatNotificationStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;

/**
 * 
 * @author bdferris
 */
@Singleton
public class SiriClientSubscriptionManager {

  private static Logger _log = LoggerFactory.getLogger(SiriClientSubscriptionManager.class);

  /**
   * This contains active channels, indexed by address. Note that we use a
   * ConcurrentMap here, so we can safely READ values in the map concurrently,
   * since not all methods accessing the map are synchronized. If the map is to
   * be modified, a global lock on {@link SiriClientSubscriptionManager} should
   * be retained for thread-safety.
   */
  private ConcurrentMap<String, ClientSubscriptionChannel> _activeChannels = new ConcurrentHashMap<String, ClientSubscriptionChannel>();

  /**
   * This contains active subscriptions, indexed by subscription id. Note that
   * we use a ConcurrentMap here, so that we can safely READ values in the map
   * concurrently, since not all the methods accessing the map are synchronized.
   * If the map is to be modified, a global lock on
   * {@link SiriClientSubscriptionManager} should be retained for thread-sa
   */
  private ConcurrentMap<SubscriptionId, ClientSubscriptionInstance> _activeSubscriptions = new ConcurrentHashMap<SubscriptionId, ClientSubscriptionInstance>();

  private SiriClientHandler _client;

  private SchedulingService _schedulingService;

  private InitiateSubscriptionsManager _initiateSubscriptionsManager;

  private CheckStatusManager _checkStatusManager;

  private TerminateSubscriptionsManager _terminateSubscriptionsManager;

  @Inject
  void setClient(SiriClientHandler client) {
    _client = client;
  }

  @Inject
  public void setSchedulingService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  @Inject
  void setInitiateSubscriptionManager(
      InitiateSubscriptionsManager initiateSubscriptionsManager) {
    _initiateSubscriptionsManager = initiateSubscriptionsManager;
  }

  @Inject
  void setCheckStatusManager(CheckStatusManager checkStatusManager) {
    _checkStatusManager = checkStatusManager;
  }

  @Inject
  void setTerminateSubscriptionsManager(
      TerminateSubscriptionsManager terminateSubscriptionsManager) {
    _terminateSubscriptionsManager = terminateSubscriptionsManager;
  }

  /****
   * Public Methods
   *****/

  /**
   * Register a pending subscription request. We don't actually consider a
   * subscription active until we receive a <SubscriptionResponse/> message,
   * which may arrive asynchronously or perhaps not at all.
   * 
   * @param request the SiriClientRequest that is responsible for the
   *          subscription request
   * @param subscriptionRequest the physical subscription request structure
   */
  public void registerPendingSubscription(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    _initiateSubscriptionsManager.registerPendingSubscription(request,
        subscriptionRequest);
  }

  public void clearPendingSubscription(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    _initiateSubscriptionsManager.clearPendingSubscription(request,
        subscriptionRequest);
  }

  /**
   * Handle an incoming subscription response message, matching the response to
   * a pending subscription request (typically registered with
   * {@link #registerPendingSubscription(SiriClientRequest, SubscriptionRequest)}
   * ), and upgrade the subscription to active status, assuming there are no
   * errors.
   * 
   * @param response
   */
  public void handleSubscriptionResponse(SubscriptionResponseStructure response) {

    _initiateSubscriptionsManager.handleSubscriptionResponse(response);

  }

  public boolean isSubscriptionActiveForModuleDelivery(
      AbstractServiceDeliveryStructure moduleDelivery) {

    SubscriptionId id = ClientSupport.getSubscriptionIdForModuleDelivery(moduleDelivery);
    return _activeSubscriptions.containsKey(id);
  }

  public SiriChannelInfo getChannelInfoForServiceDelivery(
      ServiceDelivery serviceDelivery) {

    SiriChannelInfo channelInfo = new SiriChannelInfo();

    ClientSubscriptionChannel clientSubscriptionChannel = null;

    String address = serviceDelivery.getAddress();
    if (address != null)
      clientSubscriptionChannel = _activeChannels.get(address);

    ParticipantRefStructure producerRef = serviceDelivery.getProducerRef();
    if (producerRef != null && producerRef.getValue() != null) {
      ClientSubscriptionChannel other = _activeChannels.get(producerRef.getValue());
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

    List<String> allPendings = _terminateSubscriptionsManager.requestTerminationOfSubscriptions(
        _activeSubscriptions.values(), false);

    if (waitForTerminateSubscriptionResponseOnExit)
      _terminateSubscriptionsManager.waitForPendingSubscriptionTerminationResponses(
          allPendings, _schedulingService.getResponseTimeout());
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
      channel = _activeChannels.get(producerRef.getValue());
    }

    if (channel == null && heartbeat.getAddress() != null)
      channel = _activeChannels.get(heartbeat.getAddress());

    if (channel != null) {
      synchronized (channel) {
        resetHeartbeat(channel, channel.getHeartbeatInterval());
      }
    }
  }

  /****
   * Package Methods
   * 
   * These are methods that are semi-public for support classes in this package,
   * but that should not be exposed publically.
   ****/

  /**
   * After receiving a valid SubscriptionResponse to a SubscriptionRequest, this
   * method updates the pending subscription to 'active' status.
   * 
   * Implementation note: This method is synchronized because it jointly
   * modifies {@link #_activeSubscriptions} and {@link #_activeChannels}.
   * 
   * @param response the general subscription response message
   * @param status the specific subscription response status message for the
   *          specific subscription
   * @param subscriptionId the subscription id
   * @param moduleType
   * @param moduleRequest
   * @param originalSubscriptionRequest
   */
  synchronized void upgradePendingSubscription(
      SubscriptionResponseStructure response, StatusResponseStructure status,
      SubscriptionId subscriptionId, ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleRequest,
      SiriClientRequest originalSubscriptionRequest) {

    ClientSubscriptionChannel channel = getChannelForServer(
        originalSubscriptionRequest.getTargetUrl(),
        originalSubscriptionRequest.getTargetVersion());

    ScheduledFuture<?> expiration = registerSubscriptionExpirationTask(
        subscriptionId, status, originalSubscriptionRequest);

    /**
     * Create the actual subscription instance
     */

    ClientSubscriptionInstance instance = new ClientSubscriptionInstance(
        channel, subscriptionId, originalSubscriptionRequest, moduleType,
        moduleRequest, expiration);

    ClientSubscriptionInstance existing = _activeSubscriptions.put(
        subscriptionId, instance);

    if (existing != null) {
      _log.info("overwriting existing subscription: " + subscriptionId);
    }

    Set<SubscriptionId> channelSubscriptions = channel.getSubscriptions();
    channelSubscriptions.add(subscriptionId);

    updateChannelWithClientRequest(channel, originalSubscriptionRequest,
        response);
  }

  ESiriModuleType getModuleTypeForSubscriptionId(SubscriptionId subId) {
    ClientSubscriptionInstance instance = _activeSubscriptions.get(subId);
    if (instance == null)
      return null;
    return instance.getModuleType();
  }

  /**
   * Request that the subscription with the specified id be terminated (with an
   * actual TerminateSubscriptionRequest to the SIRI endpoint). Once the
   * subscription termination is complete, the subscription will be
   * reestablished.
   * 
   * @param subscriptionId the target subscription id
   */
  void requestSubscriptionTerminationAndResubscription(
      SubscriptionId subscriptionId) {

    ClientSubscriptionInstance instance = _activeSubscriptions.get(subscriptionId);

    if (instance == null) {
      _log.warn("no subscription found to unsubscribe/resubscribe with id={}",
          subscriptionId);
      return;
    }

    /**
     * Terminate the subscription, indicating that we want to re-subscribe
     */
    _terminateSubscriptionsManager.requestTerminationOfSubscription(instance,
        true);
  }

  /**
   * Remove the specified subscription, removing it from the active subscription
   * list. If the subscription instance is the last subscription for a channel,
   * the channel is removed as well.
   * 
   * Note that no TerminateSubscriptionRequest is sent as part of the removal.
   * It's assumed that subscription termination negotiations with the SIRI
   * endpoint have already taken place (or that the SIRI endpoint is down).
   * 
   * Implementation note: This method is synchronized because it jointly
   * modified {@link #_activeSubscriptions} and {@link #_activeChannels}.
   * 
   * @param subscriptionId the id of the subscription to remove
   */
  synchronized void removeSubscription(SubscriptionId subscriptionId) {

    ClientSubscriptionInstance instance = _activeSubscriptions.remove(subscriptionId);

    if (instance == null) {
      _log.warn("subscription has already been removed: {}", subscriptionId);
      return;
    }

    ScheduledFuture<?> expirationTask = instance.getExpirationTask();
    if (expirationTask != null) {
      expirationTask.cancel(true);
    }

    ClientSubscriptionChannel channel = instance.getChannel();
    Set<SubscriptionId> subscriptions = channel.getSubscriptions();
    subscriptions.remove(instance.getSubscriptionId());

    if (subscriptions.isEmpty()) {

      _log.debug("channel has no more subscriptions: {}", channel.getAddress());

      _checkStatusManager.resetCheckStatusTask(channel, 0);
      resetHeartbeat(channel, 0);

      _activeChannels.remove(channel.getAddress());
    }
  }

  /**
   * In response to a channel connection status failure (typically either
   * through a CheckStatus or Heartbeat failure), call this method to terminate
   * the channel and all its subscriptions. Note that subscription termination
   * requests will NOT be sent (maybe they should be?). When the channel
   * termination is complete, all the channel subscriptions requests will be
   * resent in order to establish the channel.
   * 
   * @param channel the channel to disconnect and reconncet
   */
  void handleChannelDisconnectAndReconnect(ClientSubscriptionChannel channel) {

    _log.info("channel disconnect: {}", channel.getAddress());

    /**
     * Our strategy is to iterate over the subscriptions of a channel, removing
     * the subscription and saving the original SiriClientRequest that
     * established the subscription.
     */
    List<SiriClientRequest> originalSubscriptionRequests = new ArrayList<SiriClientRequest>();

    Set<SubscriptionId> channelSubscriptionIds = new HashSet<SubscriptionId>(
        channel.getSubscriptions());

    for (SubscriptionId subscriptionId : channelSubscriptionIds) {

      ClientSubscriptionInstance instance = _activeSubscriptions.get(subscriptionId);
      if (instance != null)
        originalSubscriptionRequests.add(instance.getRequest());

      removeSubscription(subscriptionId);
    }

    /**
     * When all the subscriptions have been removed (and the channel removed as
     * result), we re-send all the subscription requests to reestablish the
     * channel.
     */
    for (SiriClientRequest request : originalSubscriptionRequests)
      _client.handleRequest(request);
  }

  /****
   * Private Methods
   ****/

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

      channel.setManageSubscriptionUrl(request.getManageSubscriptionUrl());
      channel.setCheckStatusUrl(request.getCheckStatusUrl());

      channel.setReconnectionAttempts(request.getReconnectionAttempts());
      channel.setReconnectionInterval(request.getReconnectionInterval());
      channel.setContext(request.getChannelContext());

      long heartbeatInterval = request.getHeartbeatInterval();

      if (heartbeatInterval != channel.getHeartbeatInterval()) {
        channel.setHeartbeatInterval(heartbeatInterval);
        resetHeartbeat(channel, heartbeatInterval);
      }

      long checkStatusInterval = request.getCheckStatusInterval();
      channel.setCheckStatusInterval(checkStatusInterval);
      _checkStatusManager.resetCheckStatusTask(channel, checkStatusInterval);
    }
  }

  private void resetHeartbeat(ClientSubscriptionChannel channel,
      long heartbeatInterval) {

    ScheduledFuture<?> heartbeatTask = channel.getHeartbeatTask();
    if (heartbeatTask != null) {
      heartbeatTask.cancel(true);
      channel.setHeartbeatTask(null);
    }

    if (heartbeatInterval > 0) {
      ClientHeartbeatTimeoutTask task = new ClientHeartbeatTimeoutTask(this,
          channel);

      // Why is this * 2? We want to give the heartbeat a little slack time.
      // Could be better...
      heartbeatTask = _schedulingService.schedule(task, heartbeatInterval * 2,
          TimeUnit.SECONDS);
      channel.setHeartbeatTask(heartbeatTask);
    }
  }

  private synchronized ClientSubscriptionChannel getChannelForServer(
      String address, ESiriVersion targetVersion) {

    ClientSubscriptionChannel channel = _activeChannels.get(address);

    if (channel == null) {

      ClientSubscriptionChannel newChannel = new ClientSubscriptionChannel(
          address, targetVersion);

      channel = _activeChannels.putIfAbsent(address, newChannel);
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

  /**
   * When a subscription is updated to active, we register an expiration task
   * that will terminate and reestablish a subscription when its expiration time
   * is reached, based on either the
   * {@link SiriClientRequest#getInitialTerminationDuration()} or the
   * {@link StatusResponseStructure#getValidUntil()} timestamp, if present.
   */
  private ScheduledFuture<?> registerSubscriptionExpirationTask(
      SubscriptionId subscriptionId, StatusResponseStructure status,
      SiriClientRequest originalSubscriptionRequest) {

    /**
     * No matter what, the original subscription request should have included an
     * initial termination time.
     */
    Date validUntil = new Date(System.currentTimeMillis()
        + originalSubscriptionRequest.getInitialTerminationDuration());

    /**
     * If the subscription response status included a "validUntil" timestamp, we
     * use that as the expiration if it comes before the subscription initial
     * termination time.
     */
    if (status.getValidUntil() != null
        && validUntil.after(status.getValidUntil()))
      validUntil = status.getValidUntil();

    _log.debug("subscription is valid until {}", validUntil);

    ScheduledFuture<?> expiration = null;

    long delay = validUntil.getTime() - System.currentTimeMillis();
    if (delay > 0) {
      SubscriptionExpirationTask task = new SubscriptionExpirationTask(this,
          subscriptionId);
      expiration = _schedulingService.schedule(task, delay,
          TimeUnit.MILLISECONDS);
    } else {
      _log.warn(
          "subscription has already expired before it had a chance to start: id={} validUntil={}",
          subscriptionId, validUntil);
    }
    return expiration;
  }
}
