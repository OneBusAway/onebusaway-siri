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
package org.onebusaway.siri.core.subscriptions.client;

import java.util.concurrent.ScheduledFuture;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

import uk.org.siri.siri.AbstractSubscriptionStructure;

/**
 * Captures information about an active client-to-server subscription.
 * 
 * @author bdferris
 * 
 */
class ClientSubscriptionInstance {

  private final ClientSubscriptionChannel channel;

  private final SubscriptionId subscriptionId;

  private final SiriClientRequest request;

  private final ESiriModuleType moduleType;

  private final AbstractSubscriptionStructure moduleRequest;

  private final ScheduledFuture<?> expirationTask;

  public ClientSubscriptionInstance(ClientSubscriptionChannel channel,
      SubscriptionId subscriptionId, SiriClientRequest request,
      ESiriModuleType moduleType, AbstractSubscriptionStructure moduleRequest,
      ScheduledFuture<?> expirationTask) {
    this.channel = channel;
    this.subscriptionId = subscriptionId;
    this.request = request;
    this.moduleType = moduleType;
    this.moduleRequest = moduleRequest;
    this.expirationTask = expirationTask;
  }

  public ClientSubscriptionChannel getChannel() {
    return channel;
  }

  public SubscriptionId getSubscriptionId() {
    return subscriptionId;
  }

  public SiriClientRequest getRequest() {
    return request;
  }

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public AbstractSubscriptionStructure getModuleRequest() {
    return moduleRequest;
  }

  public ScheduledFuture<?> getExpirationTask() {
    return expirationTask;
  }
}