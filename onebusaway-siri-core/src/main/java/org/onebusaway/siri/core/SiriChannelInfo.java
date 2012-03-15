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
package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.List;

import uk.org.siri.siri.ServiceDelivery;

/**
 * Information associated with a particular SIRI channel. Every SIRI
 * subscription has a data channel associated with it. Multiple subscriptions
 * can refer to the same channel, in fact.
 * 
 * @author bdferris
 * 
 */
public class SiriChannelInfo {

  private List<SiriClientRequest> siriClientRequests = new ArrayList<SiriClientRequest>();

  private Object context;

  /**
   * Recall that a particular {@link ServiceDelivery} can potentially be
   * associated with multiple client requests, as the SIRI endpoint may group
   * responses to multiple requests into a single delivery.
   * 
   * @return the set of client requests associated with deliveries on this
   *         channel
   */
  public List<SiriClientRequest> getSiriClientRequests() {
    return siriClientRequests;
  }

  public void setSiriClientRequests(List<SiriClientRequest> siriClientRequests) {
    this.siriClientRequests = siriClientRequests;
  }

  /**
   * User-supplied context data associated with this channel. See
   * {@link SiriClientRequest#getChannelContext()}. Think of this a
   * user-supplied callback data associated with your subscription channel.
   * 
   * @param <T>
   * @return any user-supplied context data associated with this channel, or
   *         null if not set
   */
  @SuppressWarnings("unchecked")
  public <T> T getContext() {
    return (T) context;
  }

  public void setContext(Object context) {
    this.context = context;
  }
}
