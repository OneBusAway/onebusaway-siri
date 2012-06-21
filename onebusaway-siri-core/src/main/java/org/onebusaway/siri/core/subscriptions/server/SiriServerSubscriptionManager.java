/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2012 Google, Inc.
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
package org.onebusaway.siri.core.subscriptions.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.datatype.Duration;

import org.onebusaway.collections.tuple.T2;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriMissingArgumentException;
import org.onebusaway.siri.core.filters.ModuleDeliveryFilterFactory;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterMatcher;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.handlers.SiriSubscriptionManagerListener;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.status_exporter.StatusProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.HeartbeatNotificationStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionContextStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

@Singleton
public class SiriServerSubscriptionManager implements StatusProviderService {

  /****
   * Implementation Note:
   * 
   * The SiriSubscriptionManager is thread-safe, but we handle synchronization
   * in a slightly complex way. The primary methods for subscription management
   * ( {@link #handleSubscriptionRequest(SubscriptionRequest, ESiriVersion)},
   * {@link #terminateSubscriptionChannel(ServerSubscriptionChannel)}, and
   * {@link #terminateSubscriptionWithId(SubscriptionId)}) are all centrally
   * synchronized around the manager object. The idea here is that subscription
   * subscribe / un-subscribe events are relatively uncommon, so there shouldn't
   * be a performance bottle-neck around a central lock.
   * 
   * That said, we still use {@link ConcurrentMap} instances for managing the
   * set of channels and the set of per-module subscriptions, such that more
   * frequent methods that READ these values (as opposed to modifying) (see
   * {@link #publish(ServiceDelivery)} and
   * {@link #getActiveSubscriptionChannels()}) can access them without being
   * blocked by a subscription event.
   ****/

  private static Logger _log = LoggerFactory.getLogger(SiriServerSubscriptionManager.class);

  private SchedulingService _schedulingService;

  private ServerSupport _support = new ServerSupport();

  private SiriClientHandler _client;

  private SiriServer _server;

  private Map<SubscriptionId, ServerSubscriptionInstance> _activeSubscriptionsById = new HashMap<SubscriptionId, ServerSubscriptionInstance>();

  private Map<String, Set<SubscriptionId>> _activeSubscriptionsBySubscriberId = new HashMap<String, Set<SubscriptionId>>();

  private ConcurrentMap<String, ServerSubscriptionChannel> _channelsByAddress = new ConcurrentHashMap<String, ServerSubscriptionChannel>();

  private Map<ESiriModuleType, ConcurrentMap<SubscriptionId, ServerSubscriptionInstance>> _subscriptionsByModuleType = new HashMap<ESiriModuleType, ConcurrentMap<SubscriptionId, ServerSubscriptionInstance>>();

  private ModuleDeliveryFilterFactory _deliveryFilterFactory = new ModuleDeliveryFilterFactory();

  private List<T2<SiriModuleDeliveryFilterMatcher, SiriModuleDeliveryFilter>> _filters = new ArrayList<T2<SiriModuleDeliveryFilterMatcher, SiriModuleDeliveryFilter>>();

  private List<SiriSubscriptionManagerListener> _listeners = new ArrayList<SiriSubscriptionManagerListener>();

  private Map<String, String> _consumerAddressDefaultsByRequestorRef = new HashMap<String, String>();

  private String _consumerAddressDefault = null;

  public SiriServerSubscriptionManager() {
    for (ESiriModuleType moduleType : ESiriModuleType.values()) {
      ConcurrentHashMap<SubscriptionId, ServerSubscriptionInstance> m = new ConcurrentHashMap<SubscriptionId, ServerSubscriptionInstance>();
      _subscriptionsByModuleType.put(moduleType, m);
    }
  }

  @Inject
  public void setSchedulingService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  public void addListener(SiriSubscriptionManagerListener listener) {
    _listeners.add(listener);
  }

  public void removeListener(SiriSubscriptionManagerListener listener) {
    _listeners.remove(listener);
  }

  /**
   * There may be situations where you wish to hard-code the consumer address
   * for an incoming subscription request from a particular requestor. This
   * default consumer address value will be used in the case where no consumer
   * address is included in a subscription request with the specified requestor
   * ref.
   * 
   * @param requestorRef the id of the participant
   * @param consumerAddressDefault the default consumer address
   */
  public void setConsumerAddressDefaultForRequestorRef(String requestorRef,
      String consumerAddressDefault) {
    _consumerAddressDefaultsByRequestorRef.put(requestorRef,
        consumerAddressDefault);
  }

  /**
   * There may be situations where you wish to hard-code the consumer address
   * for an incoming subscription request. This default consumer address value
   * will be used in the case where no consumer address is included in a
   * subscription request AND no consumer address has been specified for the
   * subscription requestor ref, as with
   * {@link #setConsumerAddressDefaultForRequestorRef(String, String)}.
   * 
   * @param consumerAddressDefault the default consumer address
   */
  public void setConsumerAddressDefault(String consumerAddressDefault) {
    _consumerAddressDefault = consumerAddressDefault;
  }

  public void addModuleDeliveryFilter(SiriModuleDeliveryFilterMatcher matcher,
      SiriModuleDeliveryFilter filter) {

    T2<SiriModuleDeliveryFilterMatcher, SiriModuleDeliveryFilter> tuple = Tuples.tuple(
        matcher, filter);

    _filters.add(tuple);
  }

  public void setServer(SiriServer server) {
    _server = server;
  }

  @PreDestroy
  public void stop() {
    _activeSubscriptionsById.clear();
    _activeSubscriptionsBySubscriberId.clear();
    _channelsByAddress.clear();
    for (ConcurrentMap<SubscriptionId, ServerSubscriptionInstance> m : _subscriptionsByModuleType.values()) {
      m.clear();
    }
  }

  /****
   * 
   ****/

  public List<String> getActiveSubscriptionChannels() {
    return new ArrayList<String>(_channelsByAddress.keySet());
  }

  public synchronized void handleSubscriptionRequest(
      SubscriptionRequest subscriptionRequest, ESiriVersion originalVersion,
      List<StatusResponseStructure> statuses)
      throws SiriMissingArgumentException {

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {

      List<AbstractSubscriptionStructure> subscriptionRequests = SiriLibrary.getSubscriptionRequestsForModule(
          subscriptionRequest, moduleType);

      for (AbstractSubscriptionStructure moduleRequest : subscriptionRequests) {

        handleSubscriptionRequests(subscriptionRequest, moduleType,
            moduleRequest, statuses, originalVersion);
      }
    }
  }

  public synchronized void terminateSubscriptionChannelWithAddress(
      String address) {

    ServerSubscriptionChannel existing = _channelsByAddress.remove(address);

    if (existing != null) {

      List<SubscriptionId> ids = new ArrayList<SubscriptionId>(
          existing.getSubscriptions());

      for (SubscriptionId id : ids)
        terminateSubscriptionWithId(id);

      for (SiriSubscriptionManagerListener listener : _listeners) {
        listener.subscriptionRemoved(this);
      }
    }
  }

  public synchronized void terminateSubscriptionsForRequest(
      TerminateSubscriptionRequestStructure request,
      List<TerminationResponseStatus> statuses) {

    String subscriberId = _support.getSubscriberIdForTerminateSubscriptionRequest(request);
    ParticipantRefStructure subscriberRef = SiriTypeFactory.particpantRef(subscriberId);

    if (subscriberId == null) {
      _support.addTerminateSubscriptionStatusForMissingSubscriberRef(request,
          statuses);
      return;
    }

    Set<SubscriptionId> idsToTerminate = new HashSet<SubscriptionId>();

    if (request.getAll() != null) {
      Set<SubscriptionId> ids = _activeSubscriptionsBySubscriberId.get(subscriberId);
      if (ids != null)
        idsToTerminate.addAll(ids);
    } else {
      List<SubscriptionQualifierStructure> refs = request.getSubscriptionRef();
      for (SubscriptionQualifierStructure ref : refs) {
        if (ref != null && ref.getValue() != null)
          idsToTerminate.add(new SubscriptionId(subscriberId, ref.getValue()));
      }
    }

    Date timestamp = new Date();

    for (SubscriptionId id : idsToTerminate) {

      TerminationResponseStatus status = new TerminationResponseStatus();

      /**
       * Though we can technically set this here, I think it makes more sense in
       * the parent response structure
       */
      // status.setRequestMessageRef(request.getMessageIdentifier());

      status.setResponseTimestamp(timestamp);
      status.setStatus(Boolean.TRUE);
      status.setSubscriberRef(subscriberRef);
      status.setSubscriptionRef(SiriTypeFactory.subscriptionId(id.getSubscriptionId()));

      try {

        if (_activeSubscriptionsById.containsKey(id)) {
          terminateSubscriptionWithId(id);
        } else {
          _support.setTerminationResponseErrorConditionWithUnknownSubscription(status);
        }
      } catch (Throwable ex) {

        _support.setTerminationResponseErrorConditionWithException(status, ex);
      }

      statuses.add(status);
    }
  }

  /**
   * 
   * @param id
   */
  public synchronized void terminateSubscriptionWithId(SubscriptionId id) {

    /**
     * Remove the active subscription
     */
    ServerSubscriptionInstance instance = _activeSubscriptionsById.remove(id);

    if (instance == null)
      return;

    String subscriberId = id.getSubscriberId();
    Set<SubscriptionId> ids = _activeSubscriptionsBySubscriberId.get(subscriberId);
    if (ids != null) {
      ids.remove(id);
      if (ids.isEmpty())
        _activeSubscriptionsBySubscriberId.remove(subscriberId);
    }

    ConcurrentMap<SubscriptionId, ServerSubscriptionInstance> subscriptionsForModule = _subscriptionsByModuleType.get(instance.getModuleType());
    subscriptionsForModule.remove(id);

    ServerSubscriptionChannel channel = instance.getChannel();
    Set<SubscriptionId> subscriptions = channel.getSubscriptions();
    subscriptions.remove(id);

    /**
     * If the channel is empty, we can remove it from the list of all channels
     */
    if (subscriptions.isEmpty()) {
      _channelsByAddress.remove(channel.getAddress());
      clearHeartbeatTask(channel);
    }

    for (SiriSubscriptionManagerListener listener : _listeners)
      listener.subscriptionRemoved(this);
  }

  public List<SiriServerSubscriptionEvent> publish(ServiceDelivery delivery) {

    List<SiriServerSubscriptionEvent> events = new ArrayList<SiriServerSubscriptionEvent>();

    for (ESiriModuleType moduleType : ESiriModuleType.values())
      handlePublication(moduleType, delivery, events);

    return events;
  }

  public void recordPublicationStatistics(SiriServerSubscriptionEvent event,
      long timeNeededToPublish, boolean connectionError) {
    ServerSubscriptionChannel channel = _channelsByAddress.get(event.getAddress());
    if (channel == null) {
      return;
    }
    channel.updatePublicationStatistics(event, timeNeededToPublish, connectionError);
  }

  /****
   * Private Methods
   ****/

  private String getConsumerAddressForSubscriptionRequest(
      SubscriptionRequest subscriptionRequest, String subscriberId) {

    String consumerAddress = subscriptionRequest.getAddress();

    if (subscriptionRequest.getConsumerAddress() != null)
      consumerAddress = subscriptionRequest.getConsumerAddress();
    if (consumerAddress == null)
      consumerAddress = _consumerAddressDefaultsByRequestorRef.get(subscriberId);
    if (consumerAddress == null)
      consumerAddress = _consumerAddressDefault;

    return consumerAddress;
  }

  /**
   * 
   * @param channelId
   * @param channelVersion
   * @return
   */
  private ServerSubscriptionChannel getChannelForAddress(String address,
      ESiriVersion channelVersion) {

    ServerSubscriptionChannel channel = _channelsByAddress.get(address);
    if (channel == null) {
      ServerSubscriptionChannel newChannel = new ServerSubscriptionChannel(
          address, channelVersion);
      channel = _channelsByAddress.putIfAbsent(address, newChannel);
      if (channel == null) {
        channel = newChannel;
        for (SiriSubscriptionManagerListener listener : _listeners)
          listener.subscriptionAdded(this);
      }
    }

    return channel;
  }

  private <T extends AbstractSubscriptionStructure> void handleSubscriptionRequests(
      SubscriptionRequest subscriptionRequest, ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleRequest,
      List<StatusResponseStructure> statuses, ESiriVersion originalVersion) {

    String messageId = null;
    if (subscriptionRequest.getMessageIdentifier() != null)
      messageId = subscriptionRequest.getMessageIdentifier().getValue();

    String subscriberId = _support.getSubscriberIdForSubscriptionRequest(
        subscriptionRequest, moduleRequest);

    if (subscriberId == null) {
      StatusResponseStructure status = _support.getStatusResponseWithErrorMessage(
          subscriptionRequest,
          "The specified subscription request is missing a RequestorRef and SubscriberRef",
          null);
      statuses.add(status);
      return;
    }

    String consumerAddress = getConsumerAddressForSubscriptionRequest(
        subscriptionRequest, subscriberId);

    if (consumerAddress == null) {
      StatusResponseStructure status = _support.getStatusResponseWithErrorMessage(
          subscriptionRequest,
          "The specified subscription request is missing a subscription Address",
          null);
      statuses.add(status);
      return;
    }

    ServerSubscriptionChannel channel = getChannelForAddress(consumerAddress,
        originalVersion);

    SubscriptionQualifierStructure subscriptionRef = moduleRequest.getSubscriptionIdentifier();

    if (subscriptionRef == null || subscriptionRef.getValue() == null) {
      StatusResponseStructure status = _support.getStatusResponseWithErrorMessage(
          subscriptionRequest,
          "The specified subscription request is missing a SubscriptionIdentifier",
          null);
      statuses.add(status);
      return;
    }

    String subscriptionId = subscriptionRef.getValue();

    SubscriptionId id = new SubscriptionId(subscriberId, subscriptionId);

    _log.info("subscription request: subscriberId=" + subscriberId
        + " subscriptionId=" + subscriptionId + " address=" + consumerAddress);

    ServerSubscriptionInstance existing = _activeSubscriptionsById.get(id);

    if (existing != null) {

      ServerSubscriptionChannel existingChannel = existing.getChannel();

      if (existingChannel != channel) {

        /**
         * We do not allow a new subscription when an existing subscription with
         * the same id exists on another channel. Though it could be the remote
         * client switching endpoints without first terminating the existing
         * subscription, it could also be different client attempting to hijack
         * the subscription.
         */
        StatusResponseStructure status = _support.getStatusResponseWithErrorMessage(
            subscriptionRequest,
            "A subscription already existed with the specified subscriber/subscription id on another channel",
            id);
        statuses.add(status);
        return;

      } else {
        _log.warn("overwriting existing subscription: id={} address={}", id,
            consumerAddress);
      }
    }

    List<SiriModuleDeliveryFilter> filters = computeFilterSetForSubscriptionRequest(
        subscriptionRequest, moduleType, moduleRequest);

    ServerSubscriptionInstance instance = new ServerSubscriptionInstance(id,
        channel, moduleType, messageId, moduleRequest, filters);

    _activeSubscriptionsById.put(id, instance);

    // TODO : Thread Safety
    Set<SubscriptionId> channelSubscriptions = channel.getSubscriptions();
    channelSubscriptions.add(id);

    /**
     * Subscriptions by SubscriberId
     */
    Set<SubscriptionId> ids = _activeSubscriptionsBySubscriberId.get(id.getSubscriberId());
    if (ids == null) {
      ids = new HashSet<SubscriptionId>();
      _activeSubscriptionsBySubscriberId.put(id.getSubscriberId(), ids);
    }
    ids.add(id);

    /**
     * Subscriptions by module type
     */
    ConcurrentMap<SubscriptionId, ServerSubscriptionInstance> subscriptionsForModule = _subscriptionsByModuleType.get(moduleType);
    subscriptionsForModule.put(id, instance);

    updateChannel(subscriptionRequest, channel);

    StatusResponseStructure status = _support.getStatusResponse(
        subscriptionRequest, id);
    statuses.add(status);
  }

  private List<SiriModuleDeliveryFilter> computeFilterSetForSubscriptionRequest(
      SubscriptionRequest subscriptionRequest, ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleSubscriptionRequest) {

    List<SiriModuleDeliveryFilter> filters = new ArrayList<SiriModuleDeliveryFilter>();

    /**
     * What filters apply?
     */
    for (T2<SiriModuleDeliveryFilterMatcher, SiriModuleDeliveryFilter> tuple : _filters) {
      SiriModuleDeliveryFilterMatcher matcher = tuple.getFirst();
      if (matcher.isMatch(subscriptionRequest, moduleType,
          moduleSubscriptionRequest)) {
        filters.add(tuple.getSecond());
      }
    }

    /**
     * Add the base filter
     */
    SiriModuleDeliveryFilter filter = _deliveryFilterFactory.createFilter(
        moduleType, moduleSubscriptionRequest);
    filters.add(filter);

    return filters;
  }

  private void updateChannel(SubscriptionRequest subscriptionRequest,
      ServerSubscriptionChannel channel) {

    synchronized (channel) {

      SubscriptionContextStructure context = subscriptionRequest.getSubscriptionContext();
      if (context == null)
        return;

      long heartbeatInterval = 0;
      Duration interval = context.getHeartbeatInterval();
      if (interval != null)
        heartbeatInterval = interval.getTimeInMillis(new Date());

      if (channel.getHeartbeatInterval() != heartbeatInterval) {
        channel.setHeartbeatInterval(heartbeatInterval);
        ScheduledFuture<?> task = channel.getHeartbeatTask();
        if (task != null) {
          task.cancel(true);
          channel.setHeartbeatTask(null);
        }

        if (heartbeatInterval > 0) {
          HeartbeatTask heartbeatTask = new HeartbeatTask(channel);
          task = _schedulingService.scheduleAtFixedRate(heartbeatTask,
              heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
          channel.setHeartbeatTask(task);
        }
      }
    }
  }

  private <T extends AbstractServiceDeliveryStructure> void handlePublication(
      ESiriModuleType moduleType, ServiceDelivery delivery,
      List<SiriServerSubscriptionEvent> events) {

    List<T> deliveries = SiriLibrary.getServiceDeliveriesForModule(delivery,
        moduleType);

    ConcurrentMap<SubscriptionId, ServerSubscriptionInstance> subscriptionsById = _subscriptionsByModuleType.get(moduleType);

    for (ServerSubscriptionInstance instance : subscriptionsById.values()) {

      ServiceDelivery updatedDelivery = copyDeliveryShallow(delivery);

      List<T> applicableResponses = getApplicableResponses(updatedDelivery,
          moduleType, instance, deliveries);

      if (applicableResponses == null || applicableResponses.isEmpty())
        continue;

      List<T> specifiedDeliveries = SiriLibrary.getServiceDeliveriesForModule(
          updatedDelivery, moduleType);
      SiriLibrary.copyList(applicableResponses, specifiedDeliveries);

      SubscriptionId id = instance.getId();
      ServerSubscriptionChannel channel = instance.getChannel();
      String address = channel.getAddress();
      ESiriVersion targetVersion = channel.getTargetVersion();

      SiriServerSubscriptionEvent event = new SiriServerSubscriptionEvent(id,
          address, targetVersion, updatedDelivery);
      events.add(event);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends AbstractServiceDeliveryStructure> List<T> getApplicableResponses(
      ServiceDelivery delivery, ESiriModuleType type,
      ServerSubscriptionInstance instance, List<T> responses) {

    SubscriptionId id = instance.getId();

    AbstractSubscriptionStructure moduleSub = instance.getModuleSubscription();
    List<SiriModuleDeliveryFilter> filters = instance.getFilters();

    List<T> applicable = new ArrayList<T>();

    for (T element : responses) {

      /**
       * Make a DEEP copy of the module delivery, freeing the underlying filters
       * to make arbitrary data modifications.
       * 
       * TODO: Combine common filter operations to reduce necessary work.
       */
      element = (T) SiriLibrary.deepCopyModuleDelivery(type, element);

      /**
       * Set subscriber-specific parameters
       */
      ParticipantRefStructure subscriberRef = SiriTypeFactory.particpantRef(id.getSubscriberId());
      element.setSubscriberRef(subscriberRef);

      SubscriptionQualifierStructure subcriptionRef = SiriTypeFactory.subscriptionId(id.getSubscriptionId());
      element.setSubscriptionRef(subcriptionRef);

      element.setValidUntil(moduleSub.getInitialTerminationTime());

      if (element.getResponseTimestamp() == null)
        element.setResponseTimestamp(new Date());

      if (instance.getMessageId() != null) {
        MessageQualifierStructure messageId = SiriTypeFactory.messageId(instance.getMessageId());
        element.setRequestMessageRef(messageId);
      }

      /**
       * Apply any filters
       */
      for (SiriModuleDeliveryFilter filter : filters) {
        element = (T) filter.filter(delivery, element);
        if (element == null)
          break;
      }

      if (element != null) {
        applicable.add(element);
      }
    }

    return applicable;
  }

  private ServiceDelivery copyDeliveryShallow(ServiceDelivery delivery) {

    ServiceDelivery d = new ServiceDelivery();
    d.setAddress(delivery.getAddress());
    d.setErrorCondition(delivery.getErrorCondition());
    d.setMoreData(delivery.isMoreData());
    d.setProducerRef(delivery.getProducerRef());
    d.setRequestMessageRef(delivery.getRequestMessageRef());
    d.setResponseMessageIdentifier(delivery.getResponseMessageIdentifier());
    d.setResponseTimestamp(delivery.getResponseTimestamp());
    d.setSrsName(delivery.getSrsName());
    d.setStatus(delivery.isStatus());

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {
      List<AbstractServiceDeliveryStructure> from = SiriLibrary.getServiceDeliveriesForModule(
          delivery, moduleType);
      if (!from.isEmpty()) {
        List<AbstractServiceDeliveryStructure> to = SiriLibrary.getServiceDeliveriesForModule(
            d, moduleType);
        SiriLibrary.copyList(from, to);
      }
    }

    return d;
  }

  private void clearHeartbeatTask(ServerSubscriptionChannel channel) {
    ScheduledFuture<?> heartbeatTask = channel.getHeartbeatTask();
    if (heartbeatTask != null) {
      heartbeatTask.cancel(true);
      channel.setHeartbeatTask(null);
    }
  }

  private class HeartbeatTask implements Runnable {

    private final ServerSubscriptionChannel _channel;

    public HeartbeatTask(ServerSubscriptionChannel channel) {
      _channel = channel;
    }

    @Override
    public void run() {

      HeartbeatNotificationStructure heartbeat = new HeartbeatNotificationStructure();
      heartbeat.setServiceStartedTime(new Date(
          _server.getServiceStartedTimestamp()));
      heartbeat.setStatus(Boolean.TRUE);
      heartbeat.setProducerRef(SiriTypeFactory.particpantRef(_server.getIdentity()));
      heartbeat.setMessageIdentifier(SiriTypeFactory.randomMessageId());
      heartbeat.setRequestTimestamp(new Date());

      Siri siri = new Siri();
      siri.setHeartbeatNotification(heartbeat);

      SiriClientRequest request = new SiriClientRequest();
      request.setTargetUrl(_channel.getAddress());
      request.setTargetVersion(_channel.getTargetVersion());
      request.setPayload(siri);

      try {
        _client.handleRequest(request);
      } catch (SiriConnectionException ex) {

        _log.warn("error publishing heartbeat to " + _channel.getAddress(), ex);
        // We could be a bit more lenient here?
        terminateSubscriptionChannelWithAddress(_channel.getAddress());
      }
    }
  }

  @Override
  public synchronized void getStatus(Map<String, String> status) {
    status.put("siri.server.activeChannels",
        Integer.toString(_channelsByAddress.size()));
    status.put("siri.server.activeSubscriptions",
        Integer.toString(_activeSubscriptionsById.size()));

    for (ServerSubscriptionInstance instance : _activeSubscriptionsById.values()) {
      SubscriptionId id = instance.getId();
      String prefix = "siri.server.activeSubscription["
          + id.getSubscriberId() + "," + id.getSubscriptionId() + "]";
      instance.getStatus(prefix, status);
    }

    for (ServerSubscriptionChannel channel : _channelsByAddress.values()) {
      String prefix = "siri.server.activeChannel[" + channel.getAddress() + "]";
      channel.getStatus(prefix, status);
    }
  }
}
