package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClient;

import uk.org.siri.siri.ServiceDelivery;

/**
 * Interface for handling an incoming SIRI {@link ServiceDelivery} payload,
 * typically received asynchronously from a publish/subscribe event.
 * 
 * @author bdferris
 * 
 * @see SiriClient
 */
public interface SiriServiceDeliveryHandler {

  /**
   * Handle an incoming SIRI {@link ServiceDelivery} payload, typically received
   * asynchronously from a publish/subscribe event.
   * 
   * @param channelInfo information about the subscription channel
   * @param serviceDelivery the data payload
   */
  public void handleServiceDelivery(SiriChannelInfo channelInfo,
      ServiceDelivery serviceDelivery);
}
