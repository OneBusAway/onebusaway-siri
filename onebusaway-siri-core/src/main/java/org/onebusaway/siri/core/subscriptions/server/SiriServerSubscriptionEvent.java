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

import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.ServiceDelivery;

public class SiriServerSubscriptionEvent {

  private final SubscriptionId subscriptionId;

  private final String address;

  private final ESiriVersion targetVersion;

  private final ServiceDelivery delivery;

  public SiriServerSubscriptionEvent(SubscriptionId subscriptionId, String address,
      ESiriVersion targetVersion, ServiceDelivery delivery) {
    this.subscriptionId = subscriptionId;
    this.address = address;
    this.targetVersion = targetVersion;
    this.delivery = delivery;
  }

  public SubscriptionId getSubscriptionId() {
    return subscriptionId;
  }

  public String getAddress() {
    return address;
  }

  public ESiriVersion getTargetVersion() {
    return targetVersion;
  }

  public ServiceDelivery getDelivery() {
    return delivery;
  }
}
