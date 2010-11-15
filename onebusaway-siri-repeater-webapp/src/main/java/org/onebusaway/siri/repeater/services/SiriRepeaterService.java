package org.onebusaway.siri.repeater.services;

import org.onebusaway.siri.repeater.model.exceptions.SiriRepeaterException;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SubscriptionRequest;

public interface SiriRepeaterService {

  public void handlServiceRequest(ServiceRequest request);
  
  public ServiceDelivery handlServiceRequestWithResponse(ServiceRequest request);

  public void handleSubscriptionRequest(SubscriptionRequest request) throws SiriRepeaterException;
  
  public void handleServiceDelivery(ServiceDelivery delivery);
}
