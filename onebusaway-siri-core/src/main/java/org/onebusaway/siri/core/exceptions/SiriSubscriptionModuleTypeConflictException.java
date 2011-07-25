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
