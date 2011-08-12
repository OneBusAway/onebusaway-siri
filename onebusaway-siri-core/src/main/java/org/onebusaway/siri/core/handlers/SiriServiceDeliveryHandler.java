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
