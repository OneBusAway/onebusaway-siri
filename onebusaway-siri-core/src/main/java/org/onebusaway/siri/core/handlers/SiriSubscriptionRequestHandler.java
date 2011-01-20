package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.SiriServer;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SubscriptionRequest;

/**
 * Interface for handling an incoming SIRI {@link ServiceRequest} and producing
 * an appropriate {@link ServiceDelivery}, as typical in a SIRI request/response
 * pattern.
 * 
 * @author bdferris
 * @see SiriServer
 */
public interface SiriSubscriptionRequestHandler {

  public void handleSubscriptionRequest(SubscriptionRequest request);
}
