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
package org.onebusaway.siri.core.subscriptions.server;

import java.util.List;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

import uk.org.siri.siri.AbstractSubscriptionStructure;

class ServerSubscriptionInstance {

  private final SubscriptionId id;

  private final ServerSubscriptionChannel channel;

  private final ESiriModuleType moduleType;

  private final String messageId;

  private final AbstractSubscriptionStructure moduleSubscription;

  private final List<SiriModuleDeliveryFilter> filters;

  public ServerSubscriptionInstance(SubscriptionId id,
      ServerSubscriptionChannel channel, ESiriModuleType moduleType,
      String messageId, AbstractSubscriptionStructure moduleSubscription,
      List<SiriModuleDeliveryFilter> filters) {
    this.id = id;
    this.channel = channel;
    this.moduleType = moduleType;
    this.messageId = messageId;
    this.moduleSubscription = moduleSubscription;
    this.filters = filters;
  }

  public SubscriptionId getId() {
    return id;
  }

  public ServerSubscriptionChannel getChannel() {
    return channel;
  }

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public String getMessageId() {
    return messageId;
  }

  public AbstractSubscriptionStructure getModuleSubscription() {
    return moduleSubscription;
  }

  public List<SiriModuleDeliveryFilter> getFilters() {
    return filters;
  }

  @Override
  public String toString() {
    return "SubscriptionInstance(id=" + id + " address=" + channel.getAddress()
        + ")";
  }

}