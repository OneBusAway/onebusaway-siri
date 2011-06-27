package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.onebusaway.collections.tuple.T2;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.siri.core.exceptions.SiriMissingArgumentException;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.filters.ModuleDeliveryFilterFactory;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterMatcher;
import org.onebusaway.siri.core.handlers.SiriSubscriptionManagerListener;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriSubscriptionManager {

  /****
   * Implementation Note:
   * 
   * The SiriSubscriptionManager is thread-safe, but we handle synchronization
   * in a slightly complex way. The primary methods for subscription management
   * ( {@link #handleSubscriptionRequest(SubscriptionRequest, ESiriVersion)},
   * {@link #terminateSubscriptionChannel(ServerSubscriptionChannel)}, and
   * {@link #terminateSubscriptionInstance(ServerSubscriptionInstance)}) are all
   * centrally synchronized around the manager object. The idea here is that
   * subscription subscribe / un-subscribe events are relatively uncommon, so
   * there shouldn't be a performance bottle-neck around a central lock.
   * 
   * That said, we still use {@link ConcurrentMap} instances for managing the
   * set of channels and the set of per-module subscriptions, such that more
   * frequent methods that READ these values (as opposed to modifying) (see
   * {@link #publish(ServiceDelivery)} and
   * {@link #getActiveSubscriptionChannels()}) can access them without being
   * blocked by a subscription event.
   ****/

  private static Logger _log = LoggerFactory.getLogger(SiriSubscriptionManager.class);

  private ConcurrentMap<ServerSubscriptionChannelId, ServerSubscriptionChannel> _channelsById = new ConcurrentHashMap<ServerSubscriptionChannelId, ServerSubscriptionChannel>();

  private Map<ESiriModuleType, ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance>> _subscriptionsByModuleType = new HashMap<ESiriModuleType, ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance>>();

  private ModuleDeliveryFilterFactory _deliveryFilterFactory = new ModuleDeliveryFilterFactory();

  private List<T2<SiriModuleDeliveryFilterMatcher, SiriModuleDeliveryFilter>> _filters = new ArrayList<T2<SiriModuleDeliveryFilterMatcher, SiriModuleDeliveryFilter>>();

  private List<SiriSubscriptionManagerListener> _listeners = new ArrayList<SiriSubscriptionManagerListener>();

  private Map<String, String> _consumerAddressDefaultsByRequestorRef = new HashMap<String, String>();

  private String _consumerAddressDefault = null;

  public SiriSubscriptionManager() {
    for (ESiriModuleType moduleType : ESiriModuleType.values()) {
      ConcurrentHashMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> m = new ConcurrentHashMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance>();
      _subscriptionsByModuleType.put(moduleType, m);
    }
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

  /****
   * 
   ****/

  public List<ServerSubscriptionChannelId> getActiveSubscriptionChannels() {
    return new ArrayList<ServerSubscriptionChannelId>(_channelsById.keySet());
  }

  public synchronized ServerSubscriptionChannel handleSubscriptionRequest(
      SubscriptionRequest subscriptionRequest, ESiriVersion originalVersion)
      throws SiriMissingArgumentException {

    ParticipantRefStructure subscriberRef = subscriptionRequest.getRequestorRef();
    if (subscriberRef == null || subscriberRef.getValue() == null)
      throw new SiriMissingArgumentException("RequestorRef");
    String subscriberId = subscriberRef.getValue();

    String consumerAddress = subscriptionRequest.getAddress();
    if (subscriptionRequest.getConsumerAddress() != null)
      consumerAddress = subscriptionRequest.getConsumerAddress();
    if (consumerAddress == null)
      consumerAddress = _consumerAddressDefaultsByRequestorRef.get(subscriberId);
    if (consumerAddress == null)
      consumerAddress = _consumerAddressDefault;
    if (consumerAddress == null)
      throw new SiriMissingArgumentException("ConsumerAddress");

    ServerSubscriptionChannelId channelId = new ServerSubscriptionChannelId(
        subscriberId, consumerAddress);

    ServerSubscriptionChannel channel = getChannelForId(channelId,
        originalVersion);

    for (ESiriModuleType moduleType : ESiriModuleType.values())
      handleSubscriptionRequests(moduleType, channel, subscriptionRequest);

    return channel;
  }

  public synchronized void terminateSubscriptionChannel(
      ServerSubscriptionChannel channel) {

    ServerSubscriptionChannel existing = _channelsById.remove(channel.getId());

    if (existing != null) {
      for (SiriSubscriptionManagerListener listener : _listeners) {
        listener.subscriptionRemoved(this);
      }
    }
  }

  /**
   * 
   * @param instance
   * @return true if the instance's channel no longer has any subscriptions,
   *         otherwise false
   */
  public synchronized boolean terminateSubscriptionInstance(
      ServerSubscriptionInstance instance) {

    ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> subscriptionsForModule = _subscriptionsByModuleType.get(instance.getModuleType());

    subscriptionsForModule.remove(instance.getId());

    ServerSubscriptionChannel channel = instance.getChannel();
    ConcurrentMap<String, ServerSubscriptionInstance> subscriptions = channel.getSubscriptions();
    subscriptions.remove(instance.getSubscriptionId());

    /**
     * If the channel is empty, we can remove it from the list of all channels
     */
    if (subscriptions.isEmpty())
      _channelsById.remove(channel.getId());

    for (SiriSubscriptionManagerListener listener : _listeners)
      listener.subscriptionRemoved(this);

    return subscriptions.isEmpty();
  }

  public List<ServerSubscriptionEvent> publish(ServiceDelivery delivery) {

    List<ServerSubscriptionEvent> events = new ArrayList<ServerSubscriptionEvent>();

    for (ESiriModuleType moduleType : ESiriModuleType.values())
      handlePublication(moduleType, delivery, events);

    return events;
  }

  /****
   * Private Methods
   ****/

  /**
   * 
   * @param channelId
   * @param channelVersion
   * @return
   */
  private ServerSubscriptionChannel getChannelForId(
      ServerSubscriptionChannelId channelId, ESiriVersion channelVersion) {

    ServerSubscriptionChannel channel = _channelsById.get(channelId);
    if (channel == null) {
      ServerSubscriptionChannel newChannel = new ServerSubscriptionChannel(
          channelId, channelVersion);
      channel = _channelsById.putIfAbsent(channelId, newChannel);
      if (channel == null) {
        channel = newChannel;
        for (SiriSubscriptionManagerListener listener : _listeners)
          listener.subscriptionAdded(this);
      }
    }

    return channel;
  }

  private <T extends AbstractSubscriptionStructure> void handleSubscriptionRequests(
      ESiriModuleType moduleType, ServerSubscriptionChannel channel,
      SubscriptionRequest subscriptionRequest) {

    List<AbstractSubscriptionStructure> subscriptionRequests = SiriLibrary.getSubscriptionRequestsForModule(
        subscriptionRequest, moduleType);

    /**
     * No subscriptions of the specified type? Nothing left to do then...
     */
    if (subscriptionRequests.isEmpty())
      return;

    ConcurrentMap<String, ServerSubscriptionInstance> subscriptions = channel.getSubscriptions();

    String messageId = null;
    if (subscriptionRequest.getMessageIdentifier() != null)
      messageId = subscriptionRequest.getMessageIdentifier().getValue();

    ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> subscriptionsForModule = _subscriptionsByModuleType.get(moduleType);

    for (AbstractSubscriptionStructure request : subscriptionRequests) {

      SubscriptionQualifierStructure subscriptionRef = request.getSubscriptionIdentifier();

      if (subscriptionRef == null || subscriptionRef.getValue() == null) {
        _log.warn("no SubscriptionIdentifier for subscription request");
        continue;
      }

      String subscriptionId = subscriptionRef.getValue();

      List<SiriModuleDeliveryFilter> filters = computeFilterSetForSubscriptionRequest(
          subscriptionRequest, moduleType, request);

      ServerSubscriptionInstance instance = new ServerSubscriptionInstance(
          channel, moduleType, subscriptionId, messageId, request, filters);

      ServerSubscriptionInstance existing = subscriptions.put(subscriptionId,
          instance);

      if (existing != null) {
        _log.warn("existing subscription?  What do we do here?");
      }

      ServerSubscriptionInstanceId id = instance.getId();

      ServerSubscriptionInstance otherExisting = subscriptionsForModule.put(id,
          instance);
      if (otherExisting != null) {
        _log.warn("other existing subscription?  What do we do here?");
      }

    }
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

  private <T extends AbstractServiceDeliveryStructure> void handlePublication(
      ESiriModuleType moduleType, ServiceDelivery delivery,
      List<ServerSubscriptionEvent> events) {

    List<T> deliveries = SiriLibrary.getServiceDeliveriesForModule(delivery,
        moduleType);

    ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> subscriptionsById = _subscriptionsByModuleType.get(moduleType);

    for (ServerSubscriptionInstance moduleDetails : subscriptionsById.values()) {

      ServiceDelivery updatedDelivery = copyDeliveryShallow(delivery);

      List<T> applicableResponses = getApplicableResponses(updatedDelivery,
          moduleType, moduleDetails, deliveries);

      if (applicableResponses == null || applicableResponses.isEmpty())
        continue;

      List<T> specifiedDeliveries = SiriLibrary.getServiceDeliveriesForModule(
          updatedDelivery, moduleType);
      SiriLibrary.copyList(applicableResponses, specifiedDeliveries);

      ServerSubscriptionEvent event = new ServerSubscriptionEvent(
          moduleDetails, updatedDelivery);
      events.add(event);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends AbstractServiceDeliveryStructure> List<T> getApplicableResponses(
      ServiceDelivery delivery, ESiriModuleType type,
      ServerSubscriptionInstance instance, List<T> responses) {

    ServerSubscriptionChannel channel = instance.getChannel();
    ServerSubscriptionChannelId channelId = channel.getId();

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
      ParticipantRefStructure subscriberRef = SiriTypeFactory.particpantRef(channelId.getSubscriberId());
      element.setSubscriberRef(subscriberRef);

      SubscriptionQualifierStructure subcriptionRef = SiriTypeFactory.subscriptionId(instance.getSubscriptionId());
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
}
