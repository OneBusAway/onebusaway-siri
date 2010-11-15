package org.onebusaway.siri.repeater.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.datatype.XMLGregorianCalendar;

import org.onebusaway.siri.repeater.model.SubscriptionTarget;
import org.onebusaway.siri.repeater.model.exceptions.NoSubscriptionAddressSiriException;
import org.onebusaway.siri.repeater.model.exceptions.SiriRepeaterException;
import org.onebusaway.siri.repeater.services.ServiceDeliveryPipeline;
import org.onebusaway.siri.repeater.services.ServiceDeliveryTransformation;
import org.onebusaway.siri.repeater.services.SiriRepeaterService;
import org.onebusaway.siri.repeater.services.SiriSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.StopMonitoringSubscriptionStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;

@Component
public class SiriRepeaterServiceImpl implements SiriRepeaterService {

  static Logger _log = LoggerFactory.getLogger(SiriRepeaterServiceImpl.class);

  SiriSerializationService _siriSerializationService;

  private List<ServiceDeliveryPipeline> _pipelines = new ArrayList<ServiceDeliveryPipeline>();

  private int _threadCount = 5;

  private ExecutorService _executor;

  private ApplicationContext _context;

  @Autowired
  public void setContext(ApplicationContext context) {
    _context = context;
  }

  public void setPipelines(List<ServiceDeliveryPipeline> pipelines) {
    _pipelines = pipelines;
  }

  public void setThreadCount(int threadCount) {
    _threadCount = threadCount;
  }

  @PostConstruct
  public void setup() {
    _executor = Executors.newFixedThreadPool(_threadCount);
    
    if( _pipelines.isEmpty() )
      _pipelines.add(new ServiceDeliveryPipeline());
  }

  @PreDestroy
  public void teardown() {
    if (_executor != null)
      _executor.shutdownNow();
  }

  /****
   * {@link SiriRepeaterService} Interface
   ****/

  @Override
  public void handlServiceRequest(ServiceRequest request) {

  }

  @Override
  public ServiceDelivery handlServiceRequestWithResponse(ServiceRequest request) {
    return null;
  }

  @Override
  public void handleSubscriptionRequest(SubscriptionRequest request)
      throws SiriRepeaterException {

    URI consumerAddress = getConsumerAddress(request);
    handleSubscriptionRequests(request, consumerAddress);
  }

  @Override
  public void handleServiceDelivery(ServiceDelivery delivery) {

    for (ServiceDeliveryPipeline pipeline : _pipelines) {

      ServiceDelivery transformedDelivery = applyServiceDeliveryTransformations(
          pipeline.getTransformations(), delivery);

      if (transformedDelivery != null) {
        for (SubscriptionTarget target : pipeline.getTargets()) {
          handleServiceDelivery(transformedDelivery, target);
        }
      }
    }
  }

  /****
   * Private Methods
   ****/

  private URI getConsumerAddress(SubscriptionRequest request)
      throws SiriRepeaterException {

    if (ValueLibrary.isSet(request.getConsumerAddress()))
      return ValueLibrary.getAddressAsUri(request.getConsumerAddress());
    else if (ValueLibrary.isSet(request.getAddress()))
      return ValueLibrary.getAddressAsUri(request.getAddress());
    throw new NoSubscriptionAddressSiriException();
  }

  private void handleSubscriptionRequests(SubscriptionRequest request,
      URI consumerAddress) {

    handleSubscriptionRequets(request, consumerAddress,
        request.getStopMonitoringSubscriptionRequest(),
        StopMonitoringSubscriptionStructure.class);

    handleSubscriptionRequets(request, consumerAddress,
        request.getVehicleMonitoringSubscriptionRequest(),
        VehicleMonitoringSubscriptionStructure.class);
  }

  private <T extends AbstractSubscriptionStructure> void handleSubscriptionRequets(
      SubscriptionRequest request, URI consumerAddress,
      List<T> subscriptionStructures, Class<T> subscriptionType) {

    for (T subStruct : subscriptionStructures) {

      ParticipantRefStructure subscriberRef = subStruct.getSubscriberRef() != null
          ? subStruct.getSubscriberRef() : request.getRequestorRef();
      SubscriptionQualifierStructure subscriptionRef = subStruct.getSubscriptionIdentifier();
      XMLGregorianCalendar validUntil = subStruct.getInitialTerminationTime();

      SubscriptionTarget target = new SubscriptionTarget(subscriberRef,
          subscriptionRef, validUntil, consumerAddress, null);

      addTargetToPipeline(target);
    }
  }

  private void addTargetToPipeline(SubscriptionTarget target) {
    if (_pipelines.isEmpty())
      return;
    /**
     * Pretty dumb default behavior for now
     */
    ServiceDeliveryPipeline pipeline = _pipelines.get(0);
    pipeline.addTarget(target);
  }

  private ServiceDelivery applyServiceDeliveryTransformations(
      List<ServiceDeliveryTransformation> transformations,
      ServiceDelivery delivery) {

    if (delivery == null)
      return null;

    if (transformations != null) {
      for (ServiceDeliveryTransformation transformation : transformations) {
        delivery = transformation.transform(delivery);
        if (delivery == null)
          return null;
      }

    }
    return delivery;
  }

  private void handleServiceDelivery(ServiceDelivery delivery,
      SubscriptionTarget target) {
    ServiceDeliveryToSubscriptionTargetTask task = _context.getBean(ServiceDeliveryToSubscriptionTargetTask.class);
    task.setServiceDelivery(delivery);
    task.setTarget(target);
    _executor.submit(task);
  }
}
