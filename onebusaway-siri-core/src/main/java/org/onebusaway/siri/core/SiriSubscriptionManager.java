package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterFactory;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.MessageRefStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriSubscriptionManager {

  private static Logger _log = LoggerFactory.getLogger(SiriSubscriptionManager.class);

  private Map<ESiriModuleType, ConcurrentMap<SubscriptionId, ModuleSubscriptionDetails>> _subscriptionsByType = new HashMap<ESiriModuleType, ConcurrentMap<SubscriptionId, ModuleSubscriptionDetails>>();

  private SiriModuleDeliveryFilterFactory _deliveryFilterFactory = new SiriModuleDeliveryFilterFactory();

  private List<SiriModuleDeliveryFilterSource> _deliveryFilterSources = new ArrayList<SiriModuleDeliveryFilterSource>();

  public SiriSubscriptionManager() {
    for (ESiriModuleType type : ESiriModuleType.values()) {
      ConcurrentMap<SubscriptionId, ModuleSubscriptionDetails> subscriptionDetailsBySubscriberId = new ConcurrentHashMap<SubscriptionId, ModuleSubscriptionDetails>();
      _subscriptionsByType.put(type, subscriptionDetailsBySubscriberId);
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
   * @return **
   * 
   ****/

  public SubscriptionDetails handleSubscriptionRequest(
      SubscriptionRequest subscriptionRequest) {

    String address = subscriptionRequest.getAddress();

    if (subscriptionRequest.getConsumerAddress() != null)
      address = subscriptionRequest.getConsumerAddress();

    String messageId = null;
    if (subscriptionRequest.getMessageIdentifier() != null)
      messageId = subscriptionRequest.getMessageIdentifier().getValue();
    if (messageId == null)
      messageId = UUID.randomUUID().toString();

    SubscriptionDetails details = new SubscriptionDetails(address, messageId,
        subscriptionRequest);

    for (ESiriModuleType moduleType : ESiriModuleType.values())
      handleSubscriptionRequests(moduleType, details);

    return details;
  }

  public void terminateSubscription(SubscriptionDetails details) {
    for (ModuleSubscriptionDetails moduleDetails : details.getModuleDetails()) {
      ESiriModuleType moduleType = moduleDetails.getModuleType();
      SubscriptionId id = moduleDetails.getId();

      ConcurrentMap<SubscriptionId, ModuleSubscriptionDetails> subscriptionDetailsBySubscriberId = _subscriptionsByType.get(moduleType);
      subscriptionDetailsBySubscriberId.remove(id);
    }
  }

  public void terminateSubscription(ModuleSubscriptionDetails moduleDetails) {
    terminateSubscription(moduleDetails.getDetails());
  }

  public List<SubscriptionEvent> publish(ServiceDelivery delivery) {

    List<SubscriptionEvent> events = new ArrayList<SubscriptionEvent>();

    for (ESiriModuleType moduleType : ESiriModuleType.values())
      handlePublication(moduleType, delivery, events);

    return events;
  }

  /****
   * 
   ****/

  private <T extends AbstractSubscriptionStructure> void handleSubscriptionRequests(
      ESiriModuleType moduleType, SubscriptionDetails details) {

    SubscriptionRequest subscriptionRequest = details.getSubscriptionRequest();

    List<AbstractSubscriptionStructure> subscriptionRequests = SiriLibrary.getSubscriptionRequestsForModule(
        subscriptionRequest, moduleType);

    ConcurrentMap<SubscriptionId, ModuleSubscriptionDetails> subscriptionDetailsBySubscriberId = _subscriptionsByType.get(moduleType);

    for (AbstractSubscriptionStructure request : subscriptionRequests) {

      ParticipantRefStructure participantId = request.getSubscriberRef();

      if (participantId == null || participantId.getValue() == null)
        participantId = subscriptionRequest.getRequestorRef();

      if (participantId == null || participantId.getValue() == null) {
        _log.warn("no SubscriberRef or RequestorRef for subscription request");
        continue;
      }

      SubscriptionQualifierStructure subscriptionId = request.getSubscriptionIdentifier();

      if (subscriptionId == null || subscriptionId.getValue() == null) {
        _log.warn("no SubscriptionIdentifier for subscription request");
        continue;
      }

      SubscriptionId id = new SubscriptionId(participantId.getValue(),
          subscriptionId.getValue());

      List<SiriModuleDeliveryFilter> filters = computeFilterSetForSubscriptionRequest(
          subscriptionRequest, moduleType, request);

      ModuleSubscriptionDetails moduleDetails = new ModuleSubscriptionDetails(
          details, moduleType, id, request, filters);
      details.addModuleDetails(moduleDetails);

      subscriptionDetailsBySubscriberId.put(id, moduleDetails);
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
      List<SubscriptionEvent> events) {

    List<T> deliveries = SiriLibrary.getServiceDeliveriesForModule(delivery,
        moduleType);

    ConcurrentMap<SubscriptionId, ModuleSubscriptionDetails> subscriptionDetailsBySubscriberId = _subscriptionsByType.get(moduleType);

    for (ModuleSubscriptionDetails moduleDetails : subscriptionDetailsBySubscriberId.values()) {

      ServiceDelivery updatedDelivery = copyDeliveryShallow(delivery);

      List<T> applicableResponses = getApplicableResponses(updatedDelivery,
          moduleType, moduleDetails, deliveries);

      if (applicableResponses == null || applicableResponses.isEmpty())
        continue;

      List<T> specifiedDeliveries = SiriLibrary.getServiceDeliveriesForModule(
          updatedDelivery, moduleType);
      SiriLibrary.copyList(applicableResponses, specifiedDeliveries);

      SubscriptionDetails details = moduleDetails.getDetails();
      SubscriptionRequest subscriptionRequest = details.getSubscriptionRequest();
      MessageQualifierStructure messageId = subscriptionRequest.getMessageIdentifier();

      if (messageId != null && messageId.getValue() != null) {
        MessageRefStructure messageRef = new MessageRefStructure();
        messageRef.setValue(messageId.getValue());
        updatedDelivery.setRequestMessageRef(messageRef);
      }

      SubscriptionEvent event = new SubscriptionEvent(moduleDetails,
          updatedDelivery);
      events.add(event);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends AbstractServiceDeliveryStructure> List<T> getApplicableResponses(
      ServiceDelivery delivery, ESiriModuleType type,
      ModuleSubscriptionDetails moduleDetails, List<T> responses) {

    SubscriptionDetails details = moduleDetails.getDetails();
    SubscriptionId subId = moduleDetails.getId();
    SubscriptionRequest sub = details.getSubscriptionRequest();
    AbstractSubscriptionStructure moduleSub = moduleDetails.getModuleSubscription();
    List<SiriModuleDeliveryFilter> filters = moduleDetails.getFilters();

    List<T> applicable = new ArrayList<T>();

    for (T element : responses) {

      /**
       * Make a shallow copy of the module delivery object
       */
      element = (T) SiriLibrary.copyServiceDelivery(type, element);

      /**
       * Set subscriber-specific parameters
       */
      MessageQualifierStructure messageId = sub.getMessageIdentifier();
      element.setRequestMessageRef(messageId);

      ParticipantRefStructure subscriberRef = new ParticipantRefStructure();
      subscriberRef.setValue(subId.getSubscriberId());
      element.setSubscriberRef(subscriberRef);

      SubscriptionQualifierStructure subcriptionRef = new SubscriptionQualifierStructure();
      subcriptionRef.setValue(subId.getSubscriptionId());
      element.setSubscriptionRef(subcriptionRef);

      element.setValidUntil(moduleSub.getInitialTerminationTime());

      if (element.getResponseTimestamp() == null)
        element.setResponseTimestamp(new Date());

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
