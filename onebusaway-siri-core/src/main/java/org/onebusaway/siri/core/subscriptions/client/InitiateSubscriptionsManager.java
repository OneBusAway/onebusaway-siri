/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2011 Google Inc.
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

import static org.onebusaway.siri.core.subscriptions.client.ClientSupport.appendError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.exceptions.SiriSubscriptionModuleTypeConflictException;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDeliveryErrorConditionStructure;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;

@Singleton
class InitiateSubscriptionsManager {

  private static final Logger _log = LoggerFactory.getLogger(InitiateSubscriptionsManager.class);

  /**
   * This map contains pending subscription requests, awaiting a response from
   * the server
   */
  private ConcurrentMap<SubscriptionId, ClientPendingSubscription> _pendingSubscriptionRequests = new ConcurrentHashMap<SubscriptionId, ClientPendingSubscription>();

  private SiriClientSubscriptionManager _subscriptionManager;

  private SchedulingService _schedulingService;

  private TerminateSubscriptionsManager _terminateSubscriptionManager;

  @Inject
  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  @Inject
  public void setScheduleService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  @Inject
  public void setTerminateSubscriptionManager(
      TerminateSubscriptionsManager terminateSubscriptionManager) {
    _terminateSubscriptionManager = terminateSubscriptionManager;
  }

  /**
   * @param subscriptionId
   * @return true if a subscription with the specified id is pending
   */
  public boolean isSubscriptionPending(SubscriptionId subscriptionId) {
    return _pendingSubscriptionRequests.containsKey(subscriptionId);
  }

  /**
   * Register a pending subscription by storing information necessary for
   * completing the subscription when a subscription response is eventually
   * received from the SIRI endpoint, as indicated with a call to
   * {@link #handleSubscriptionResponse(SubscriptionResponseStructure)}. In
   * addition to noting the pending subscription data, a timeout task is
   * registered as well. If the subscription response is not received within the
   * standard timeout (see
   * {@link SchedulingService#scheduleResponseTimeoutTask(Runnable)}), then the
   * pending subscription will be cleared and an error will be logged.
   * 
   * 
   * @param request the client request
   * @param subscriptionRequest the SIRI subscription request associated with
   *          that client
   */
  public void registerPendingSubscription(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    _log.debug("register pending subscription request");

    Map<SubscriptionId, ClientPendingSubscription> pendingSubscriptions = new HashMap<SubscriptionId, ClientPendingSubscription>();

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {

      List<AbstractSubscriptionStructure> requests = SiriLibrary.getSubscriptionRequestsForModule(
          subscriptionRequest, moduleType);

      for (AbstractSubscriptionStructure subRequest : requests) {

        SubscriptionId subId = getSubscriptionIdForSubscriptionRequest(
            subscriptionRequest, subRequest);

        /**
         * Check to make sure that the new subscription doesn't conflict with an
         * existing subscription, either active or pending
         */
        checkForModuleTypeConflict(subId, moduleType, pendingSubscriptions);

        ClientPendingSubscription pending = new ClientPendingSubscription(
            subId, request, moduleType, subRequest);
        pendingSubscriptions.put(subId, pending);
      }
    }

    /**
     * We hold off on adding the pending subscriptions until we are sure that
     * none of them cause conflicts.
     */
    _pendingSubscriptionRequests.putAll(pendingSubscriptions);

    /**
     * Setup an expiration timeout for the subscription response
     */
    PendingSubscriptionTimeoutTask task = new PendingSubscriptionTimeoutTask(
        pendingSubscriptions.keySet());
    _schedulingService.scheduleResponseTimeoutTask(task);
  }

  public void clearPendingSubscription(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {

      List<AbstractSubscriptionStructure> requests = SiriLibrary.getSubscriptionRequestsForModule(
          subscriptionRequest, moduleType);

      for (AbstractSubscriptionStructure subRequest : requests) {

        SubscriptionId subId = getSubscriptionIdForSubscriptionRequest(
            subscriptionRequest, subRequest);

        _pendingSubscriptionRequests.remove(subId);
      }
    }
  }

  public void handleSubscriptionResponse(SubscriptionResponseStructure response) {

    for (StatusResponseStructure status : response.getResponseStatus()) {

      SubscriptionId subId = getSubscriptionIdForStatusResponse(status);

      ClientPendingSubscription pending = _pendingSubscriptionRequests.remove(subId);

      if (pending == null) {
        logUnknownSubscriptionResponse(response, subId);
        continue;
      }

      if (status.isStatus()) {

        _subscriptionManager.upgradePendingSubscription(response, status,
            subId, pending.getModuleType(), pending.getRequest());

      } else {
        logErrorInSubscriptionResponse(response, status, subId);
      }
    }
  }

  /****
   * Private Methods
   ****/

  /**
   * Check to make sure that a new subscription request doesn't conflict with an
   * existing subscription, either active or pending.
   * 
   * @throws SiriSubscriptionModuleTypeConflictException if an exception is
   *           found
   */
  private void checkForModuleTypeConflict(SubscriptionId subId,
      ESiriModuleType moduleType,
      Map<SubscriptionId, ClientPendingSubscription> pendingSubscriptions)
      throws SiriSubscriptionModuleTypeConflictException {

    ESiriModuleType existingModuleType = _subscriptionManager.getModuleTypeForSubscriptionId(subId);

    if (existingModuleType != null && existingModuleType != moduleType) {
      logWarningAboutActiveSubscriptionsWithDifferentModuleTypes(subId,
          moduleType, existingModuleType);
      throw new SiriSubscriptionModuleTypeConflictException(subId,
          existingModuleType, moduleType);
    }

    ClientPendingSubscription pending = _pendingSubscriptionRequests.get(subId);
    if (pending != null && pending.getModuleType() != moduleType) {
      logWarningAboutPendingSubscriptionsWithDifferentModuleTypes(subId,
          moduleType, pending);
      throw new SiriSubscriptionModuleTypeConflictException(subId,
          pending.getModuleType(), moduleType);
    }

    pending = pendingSubscriptions.get(subId);
    if (pending != null && pending.getModuleType() != moduleType) {
      logWarningAboutPendingSubscriptionsWithDifferentModuleTypes(subId,
          moduleType, pending);
      throw new SiriSubscriptionModuleTypeConflictException(subId,
          pending.getModuleType(), moduleType);
    }
  }

  private SubscriptionId getSubscriptionIdForSubscriptionRequest(
      SubscriptionRequest subscriptionRequest,
      AbstractSubscriptionStructure functionalSubscriptionRequest) {

    ParticipantRefStructure subscriberRef = getBestSubscriberRef(
        functionalSubscriptionRequest.getSubscriberRef(),
        subscriptionRequest.getRequestorRef());

    SubscriptionQualifierStructure subscriptionRef = functionalSubscriptionRequest.getSubscriptionIdentifier();

    return ClientSupport.getSubscriptionId(subscriberRef, subscriptionRef);
  }

  private ParticipantRefStructure getBestSubscriberRef(
      ParticipantRefStructure... refs) {
    for (ParticipantRefStructure ref : refs) {
      if (ref != null && ref.getValue() != null)
        return ref;
    }
    return null;
  }

  private SubscriptionId getSubscriptionIdForStatusResponse(
      StatusResponseStructure status) {

    ParticipantRefStructure subscriberRef = status.getSubscriberRef();
    SubscriptionQualifierStructure subscriptionRef = status.getSubscriptionRef();

    return ClientSupport.getSubscriptionId(subscriberRef, subscriptionRef);
  }

  private void logUnknownSubscriptionResponse(
      SubscriptionResponseStructure response, SubscriptionId subId) {
    StringBuilder b = new StringBuilder();
    b.append("A <SubscriptionResponse/ResponseStatus/> was received with no pending <SubscriptionRequest/> having been sent:");
    if (response.getAddress() != null)
      b.append(" address=").append(response.getAddress());
    if (response.getSubscriptionManagerAddress() != null)
      b.append(" subscriptionManagerAddress=").append(
          response.getSubscriptionManagerAddress());
    if (response.getResponderRef() != null
        && response.getResponderRef().getValue() != null)
      b.append(" responderRef=" + response.getResponderRef().getValue());
    b.append(" subscriptionId=" + subId);
    _log.warn(b.toString());
  }

  private void logErrorInSubscriptionResponse(
      SubscriptionResponseStructure response, StatusResponseStructure status,
      SubscriptionId subId) {

    StringBuilder b = new StringBuilder();
    b.append("We received an error response for a subscription request:");
    if (response.getAddress() != null)
      b.append(" address=").append(response.getAddress());
    if (response.getSubscriptionManagerAddress() != null)
      b.append(" subscriptionManagerAddress=").append(
          response.getSubscriptionManagerAddress());
    if (response.getResponderRef() != null
        && response.getResponderRef().getValue() != null)
      b.append(" responderRef=" + response.getResponderRef().getValue());
    b.append(" subscriptionId=" + subId);
    ServiceDeliveryErrorConditionStructure error = status.getErrorCondition();

    if (error != null) {
      appendError(error.getAccessNotAllowedError(), b);
      appendError(error.getAllowedResourceUsageExceededError(), b);
      appendError(error.getCapabilityNotSupportedError(), b);
      appendError(error.getNoInfoForTopicError(), b);
      appendError(error.getOtherError(), b);

      if (error.getDescription() != null
          && error.getDescription().getValue() != null)
        b.append(" errorDescription=").append(error.getDescription().getValue());
    }

    _log.warn(b.toString());
  }

  private void logWarningAboutActiveSubscriptionsWithDifferentModuleTypes(
      SubscriptionId subId, ESiriModuleType newModuleType,
      ESiriModuleType existingModuleType) {

    _log.warn("An existing subscription ("
        + subId
        + ") already exists for module type "
        + existingModuleType
        + " but a new subscription has been requested for module type "
        + newModuleType
        + ".  Reuse of the same subscription id across different module types is not supported.");
  }

  private void logWarningAboutPendingSubscriptionsWithDifferentModuleTypes(
      SubscriptionId subId, ESiriModuleType moduleType,
      ClientPendingSubscription pending) {

    _log.warn("An existing pending subscription ("
        + subId
        + ") already exists for module type "
        + pending.getModuleType()
        + " but a new subscription has been requested for module type "
        + moduleType
        + ".  Reuse of the same subscription id across different module types is not supported.");
  }

  /****
   * 
   ****/

  private class PendingSubscriptionTimeoutTask implements Runnable {

    private final List<SubscriptionId> _subscriptionIds;

    public PendingSubscriptionTimeoutTask(Set<SubscriptionId> subscriptionIds) {
      _subscriptionIds = new ArrayList<SubscriptionId>(subscriptionIds);
    }

    @Override
    public void run() {
      for (SubscriptionId subscriptionId : _subscriptionIds) {
        ClientPendingSubscription pending = _pendingSubscriptionRequests.remove(subscriptionId);
        if (pending != null) {

          SiriClientRequest request = pending.getRequest();

          _log.warn("pending subscription expired before receiving a subscription response from server: url="
              + request.getTargetUrl()
              + " subscriptionId="
              + subscriptionId
              + " remainingConnectionAttempts="
              + request.getRemainingReconnectionAttempts()
              + " connectionErrorCount="
              + request.getConnectionErrorCount()
              + " timeout=" + _schedulingService.getResponseTimeout() + "s");

          request.incrementConnectionErrorCount();

          boolean resubscribe = request.getRemainingReconnectionAttempts() != 0;
          request.decrementRemainingReconnctionAttempts();

          _terminateSubscriptionManager.requestTerminationOfInitiatedSubscription(
              request, subscriptionId, resubscribe);
        }
      }
    }
  }
}
