/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2011 Google, Inc
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.services.StatusProviderService;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.HeartbeatNotificationStructure;
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
public class SiriClientSubscriptionManager implements StatusProviderService {

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

  private SchedulingService _schedulingService;

  private InitiateSubscriptionsManager _initiateSubscriptionsManager;

  private CheckStatusManager _checkStatusManager;

  private HeartbeatManager _heartbeatManager;

  private TerminateSubscriptionsManager _terminateSubscriptionsManager;

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
  public void setHeartbeatManager(HeartbeatManager heartbeatManager) {
    _heartbeatManager = heartbeatManager;
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

    _log.info("pending subscription: {}", request);

    _initiateSubscriptionsManager.registerPendingSubscription(request,
        subscriptionRequest);
  }

  public void clearPendingSubscription(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    _log.info("clear pending subscription: {}", request);

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

  /**
   * @param subscriptionId
   * @return true if a subscription with the specified id is active
   */
  public boolean isSubscriptionActive(SubscriptionId subscriptionId) {
    return _activeSubscriptions.containsKey(subscriptionId);
  }

  /**
   * 
   * @param moduleDelivery
   * @return
   */
  public boolean isSubscriptionActiveForModuleDelivery(
      AbstractServiceDeliveryStructure moduleDelivery) {

    SubscriptionId id = ClientSupport.getSubscriptionIdForModuleDelivery(moduleDelivery);
    return isSubscriptionActive(id);
  }

  public void recordServiceDeliveryStatistics(ServiceDelivery serviceDelivery) {
    Set<ClientSubscriptionInstance> instances = getSubscriptionInstancesForServiceDelivery(serviceDelivery);
    for (ClientSubscriptionInstance instance : instances) {
      instance.recordServiceDeliveryStatistics(serviceDelivery);
    }
  }

  public SiriChannelInfo getChannelInfoForServiceDelivery(
      ServiceDelivery serviceDelivery) {

    SiriChannelInfo channelInfo = new SiriChannelInfo();

    Set<ClientSubscriptionChannel> channels = new HashSet<ClientSubscriptionChannel>();
    Set<SiriClientRequest> requests = new HashSet<SiriClientRequest>();

    /**
     * First, try looking up by address
     */
    String address = serviceDelivery.getAddress();
    if (address != null) {
      ClientSubscriptionChannel channel = _activeChannels.get(address);
      if (channel != null) {
        channels.add(channel);
      }
    }

    /**
     * Also try looking up by a specific subscription
     */
    Set<ClientSubscriptionInstance> instances = getSubscriptionInstancesForServiceDelivery(serviceDelivery);
    for (ClientSubscriptionInstance instance : instances) {
      channels.add(instance.getChannel());
      requests.add(instance.getRequest());
    }

    channelInfo.setSiriClientRequests(new ArrayList<SiriClientRequest>(requests));

    if (!channels.isEmpty()) {
      if (channels.size() > 1) {
        _log.warn("multiple channels found for a single service delivery");
      } else {
        ClientSubscriptionChannel channel = channels.iterator().next();
        channelInfo.setContext(channel.getContext());
      }
    }

    return channelInfo;
  }

  public void handleTerminateSubscriptionResponse(
      TerminateSubscriptionResponseStructure response) {
    _terminateSubscriptionsManager.handleTerminateSubscriptionResponse(response);
  }

  public void terminateAllSubscriptions(
      boolean waitForTerminateSubscriptionResponseOnExit) {

    _log.info("terminate all subscriptions");

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

    ClientSubscriptionChannel channel = _activeChannels.get(heartbeat.getAddress());

    if (channel != null)
      _heartbeatManager.resetHeartbeat(channel, channel.getHeartbeatInterval());
  }

  /***
   * {@link StatusProviderService} Interface
   ****/

  @Override
  public void getStatus(Map<String, String> status) {
    status.put("siri.client.activeChannels",
        Integer.toString(_activeChannels.size()));
    status.put("siri.client.activeSubscriptions",
        Integer.toString(_activeSubscriptions.size()));

    for (ClientSubscriptionChannel channel : _activeChannels.values()) {
      String prefix = "siri.client.activeChannel[" + channel.getAddress() + "]";
      channel.getStatus(prefix, status);
    }

    for (ClientSubscriptionInstance instance : _activeSubscriptions.values()) {
      SubscriptionId id = instance.getSubscriptionId();
      String prefix = "siri.server.activeSubscription["
          + id.getSubscriberId() + "," + id.getSubscriptionId() + "]";
      instance.getStatus(prefix, status);
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
   * @param originalSubscriptionRequest
   */
  synchronized void upgradePendingSubscription(
      SubscriptionResponseStructure response, StatusResponseStructure status,
      SubscriptionId subscriptionId, ESiriModuleType moduleType,
      SiriClientRequest originalSubscriptionRequest) {

    _log.info("upgrade pending subscription: {}", originalSubscriptionRequest);

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
        expiration);

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
      _heartbeatManager.resetHeartbeat(channel, 0);

      _activeChannels.remove(channel.getAddress());
    }
  }

  /**
   * In response to a channel connection status failure (typically either
   * through a CheckStatus or Heartbeat failure), call this method to terminate
   * the channel and all its subscriptions. Note that subscription termination
   * requests will be sent, though responses aren't required. When the channel
   * termination is complete, all the channel subscriptions requests will be
   * resent in order to establish the channel.
   * 
   * @param channel the channel to disconnect and reconncet
   */
  void handleChannelDisconnectAndReconnect(ClientSubscriptionChannel channel) {

    _log.info("channel disconnect: {}", channel.getAddress());

    Set<SubscriptionId> channelSubscriptionIds = new HashSet<SubscriptionId>(
        channel.getSubscriptions());

    List<ClientSubscriptionInstance> instances = new ArrayList<ClientSubscriptionInstance>();

    for (SubscriptionId subscriptionId : channelSubscriptionIds) {

      ClientSubscriptionInstance instance = _activeSubscriptions.get(subscriptionId);
      if (instance != null)
        instances.add(instance);
    }

    _terminateSubscriptionsManager.requestTerminationOfSubscriptions(instances,
        true);
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

      int heartbeatInterval = request.getHeartbeatInterval();
      _heartbeatManager.resetHeartbeat(channel, heartbeatInterval);

      int checkStatusInterval = request.getCheckStatusInterval();
      _checkStatusManager.resetCheckStatusTask(channel, checkStatusInterval);
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
   * 
   * @param serviceDelivery
   * @return the set of
   */
  private Set<ClientSubscriptionInstance> getSubscriptionInstancesForServiceDelivery(
      ServiceDelivery serviceDelivery) {
    Set<ClientSubscriptionInstance> instances = new HashSet<ClientSubscriptionInstance>();
    /**
     * Also try looking up by a specific subscription
     */
    for (ESiriModuleType moduleType : ESiriModuleType.values()) {
      List<AbstractServiceDeliveryStructure> moduleDeliveries = SiriLibrary.getServiceDeliveriesForModule(
          serviceDelivery, moduleType);
      for (AbstractServiceDeliveryStructure moduleDelivery : moduleDeliveries) {
        if (ClientSupport.hasSubscriptionIdForModuleDelivery(moduleDelivery)) {
          SubscriptionId subscriptionId = ClientSupport.getSubscriptionIdForModuleDelivery(moduleDelivery);
          ClientSubscriptionInstance instance = _activeSubscriptions.get(subscriptionId);
          if (instance != null) {
            instances.add(instance);
          }
        }
      }
    }
    return instances;
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
    long delay = originalSubscriptionRequest.getInitialTerminationDuration();

    /**
     * If the subscription response status included a "validUntil" timestamp, we
     * use that as the expiration if it comes before the subscription initial
     * termination time.
     */
    if (status.getValidUntil() != null) {
      Date validUntil = status.getValidUntil();
      long newDelay = validUntil.getTime() - System.currentTimeMillis();
      if (newDelay < delay)
        delay = newDelay;
    }

    _log.debug("subscription is valid for {} secs", (delay / 1000));

    ScheduledFuture<?> expiration = null;

    if (delay > 0) {
      SubscriptionExpirationTask task = new SubscriptionExpirationTask(this,
          subscriptionId);
      expiration = _schedulingService.schedule(task, delay,
          TimeUnit.MILLISECONDS);
    } else {
      _log.warn(
          "subscription has already expired before it had a chance to start: id={} delay={}",
          subscriptionId, (delay / 1000));
    }
    return expiration;
  }
}
