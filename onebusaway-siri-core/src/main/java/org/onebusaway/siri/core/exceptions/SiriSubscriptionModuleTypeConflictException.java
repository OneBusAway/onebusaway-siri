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
package org.onebusaway.siri.core.exceptions;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

public class SiriSubscriptionModuleTypeConflictException extends SiriException {

  private static final long serialVersionUID = 1L;

  private final SubscriptionId _subscriptionId;

  private final ESiriModuleType _existingModuleType;

  private final ESiriModuleType _pendingModuleType;

  public SiriSubscriptionModuleTypeConflictException(
      SubscriptionId subscriptionId, ESiriModuleType existingModuleType,
      ESiriModuleType pendingModuleType) {
    super(
        "A pending subscription request conflicts with an existing subscription, with the two subscriptions having different functional module types: id=("
            + subscriptionId
            + ") existingModuleType="
            + existingModuleType
            + " pendingModuleType=" + pendingModuleType);
    _subscriptionId = subscriptionId;
    _existingModuleType = existingModuleType;
    _pendingModuleType = pendingModuleType;
  }

  public SubscriptionId getSubscriptionId() {
    return _subscriptionId;
  }

  public ESiriModuleType getExistingModuleType() {
    return _existingModuleType;
  }

  public ESiriModuleType getPendingModuleType() {
    return _pendingModuleType;
  }
}
