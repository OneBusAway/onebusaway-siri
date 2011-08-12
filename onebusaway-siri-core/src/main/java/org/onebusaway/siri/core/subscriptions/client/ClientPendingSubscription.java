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

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

import uk.org.siri.siri.AbstractSubscriptionStructure;

class ClientPendingSubscription {

  private final SubscriptionId id;

  private final SiriClientRequest request;

  private final ESiriModuleType moduleType;

  private final AbstractSubscriptionStructure moduleRequest;

  public ClientPendingSubscription(SubscriptionId id,
      SiriClientRequest request, ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleRequest) {
    this.id = id;
    this.request = request;
    this.moduleType = moduleType;
    this.moduleRequest = moduleRequest;
  }

  public SubscriptionId getId() {
    return id;
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
}
