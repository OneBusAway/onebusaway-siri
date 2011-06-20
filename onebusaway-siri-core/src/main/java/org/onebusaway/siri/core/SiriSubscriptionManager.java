package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.onebusaway.siri.core.exceptions.SiriMissingArgumentException;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterFactory;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterSource;
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

  private static Logger _log = LoggerFactory.getLogger(SiriSubscriptionManager.class);

  private ConcurrentMap<ServerSubscriptionChannelId, ServerSubscriptionChannel> _channelsById = new ConcurrentHashMap<ServerSubscriptionChannelId, ServerSubscriptionChannel>();

  private Map<ESiriModuleType, ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance>> _subscriptions = new HashMap<ESiriModuleType, ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance>>();

  private SiriModuleDeliveryFilterFactory _deliveryFilterFactory = new SiriModuleDeliveryFilterFactory();

  private List<SiriModuleDeliveryFilterSource> _deliveryFilterSources = new ArrayList<SiriModuleDeliveryFilterSource>();

  private String _consumerAddressDefault = null;

  public SiriSubscriptionManager() {
    for (ESiriModuleType moduleType : ESiriModuleType.values()) {
      ConcurrentHashMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> m = new ConcurrentHashMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance>();
      _subscriptions.put(moduleType, m);
    }
  }

  public void addModuleDeliveryFilterSource(
      SiriModuleDeliveryFilterSource source) {
    _deliveryFilterSources.add(source);
  }

  public void removeModuleDeliveryFilterSource(
      SiriModuleDeliveryFilterSource source) {
    _deliveryFilterSources.remove(source);
  }

  /**
   * There may be situations where you wish to hard-code the consumer address
   * for any incoming subscription requests. This default consumer address value
   * will be used in the case where no consumer address is included in a
   * subscription request.
   * 
   * @param consumerAddressDefault
   */
  public void setConsumerAddressDefault(String consumerAddressDefault) {
    _consumerAddressDefault = consumerAddressDefault;
  }

  /****
   * 
   ****/

  public ServerSubscriptionChannel handleSubscriptionRequest(
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

  public void terminateSubscriptionChannel(ServerSubscriptionChannel channel) {
    _channelsById.remove(channel.getId());
  }

  /**
   * 
   * @param instance
   * @return true if the instance's channel no longer has any subscriptions,
   *         otherwise false
   */
  public boolean terminateSubscriptionInstance(
      ServerSubscriptionInstance instance) {

    ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> subscriptionsForModule = _subscriptions.get(instance.getModuleType());

    subscriptionsForModule.remove(instance.getId());

    ServerSubscriptionChannel channel = instance.getChannel();
    ConcurrentMap<String, ServerSubscriptionInstance> subscriptions = channel.getSubscriptions();
    subscriptions.remove(instance.getSubscriptionId());

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
      if (channel == null)
        channel = newChannel;
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

    ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> subscriptionsForModule = _subscriptions.get(moduleType);

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
    for (SiriModuleDeliveryFilterSource filterSource : _deliveryFilterSources)
      filterSource.addFiltersForModuleSubscription(subscriptionRequest,
          moduleType, moduleSubscriptionRequest, filters);

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

    ConcurrentMap<ServerSubscriptionInstanceId, ServerSubscriptionInstance> subscriptionsById = _subscriptions.get(moduleType);

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
       * Make a shallow copy of the module delivery object
       */
      element = (T) SiriLibrary.copyServiceDelivery(type, element);

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
