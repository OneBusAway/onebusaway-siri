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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SchedulingService;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.exceptions.SiriSubscriptionModuleTypeConflictException;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

import uk.org.siri.siri.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;

public class InitiateSubscriptionsManagerTest {

  private InitiateSubscriptionsManager _manager;

  private SiriClientHandler _client;

  private SiriClientSubscriptionManager _subscriptionManager;

  private SchedulingService _schedulingService;

  @Before
  public void setup() {

    _manager = new InitiateSubscriptionsManager();

    _client = Mockito.mock(SiriClientHandler.class);
    _manager.setClient(_client);

    _subscriptionManager = Mockito.mock(SiriClientSubscriptionManager.class);
    _manager.setSubscriptionManager(_subscriptionManager);

    _schedulingService = Mockito.mock(SchedulingService.class);
    _manager.setScheduleService(_schedulingService);
  }

  /**
   * Initiate a pending subscription request and then send a response. The
   * default case.
   */
  @Test
  public void testSubscriptionRequestAndResponse() {

    SubscriptionId subId = new SubscriptionId("userA", "subId");
    assertFalse(_manager.isSubscriptionPending(subId));

    /**
     * Construct the request
     */
    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    _manager.registerPendingSubscription(request, subscriptionRequest);

    assertTrue(_manager.isSubscriptionPending(subId));

    /**
     * A response timeout task should be created as part of the pending
     * subscription
     */
    Mockito.verify(_schedulingService).scheduleResponseTimeoutTask(
        Mockito.any(Runnable.class));

    /**
     * The service should check with the subscription manager to see if an
     * existing subscription with the specified id already exists
     */
    Mockito.verify(_subscriptionManager).getModuleTypeForSubscriptionId(subId);

    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();

    StatusResponseStructure status = new StatusResponseStructure();
    status.setSubscriberRef(SiriTypeFactory.particpantRef("userA"));
    status.setSubscriptionRef(vmRequest.getSubscriptionIdentifier());
    status.setStatus(Boolean.TRUE);
    response.getResponseStatus().add(status);

    _manager.handleSubscriptionResponse(response);

    Mockito.verify(_subscriptionManager).upgradePendingSubscription(response,
        status, subId, ESiriModuleType.VEHICLE_MONITORING, request);
    assertFalse(_manager.isSubscriptionPending(subId));

    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);
  }

  /**
   * Test that the subscriberId is properly pulled from the "subscriberId" field
   * of the VM subscription.
   */
  @Test
  public void testSubscriptionRequestAndResponseWithSpecificSubscriberRef() {

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriberRef(SiriTypeFactory.particpantRef("userB"));
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    _manager.registerPendingSubscription(request, subscriptionRequest);

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();

    StatusResponseStructure status = new StatusResponseStructure();
    status.setSubscriberRef(SiriTypeFactory.particpantRef("userB"));
    status.setSubscriptionRef(vmRequest.getSubscriptionIdentifier());
    status.setStatus(Boolean.TRUE);
    response.getResponseStatus().add(status);

    _manager.handleSubscriptionResponse(response);

    SubscriptionId subId = new SubscriptionId("userB", "subId");
    Mockito.verify(_subscriptionManager).upgradePendingSubscription(response,
        status, subId, ESiriModuleType.VEHICLE_MONITORING, request);
  }

  /**
   * Test that the subscriberId is properly pulled from the "subscriberId" field
   * of the VM subscription, even if the "requestorRef" is set in the parent
   * SubscriptionRequest.
   */
  @Test
  public void testSubscriptionRequestAndResponseWithSpecificSubscriberRefOverride() {

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriberRef(SiriTypeFactory.particpantRef("userB"));
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    _manager.registerPendingSubscription(request, subscriptionRequest);

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();

    StatusResponseStructure status = new StatusResponseStructure();
    status.setSubscriberRef(SiriTypeFactory.particpantRef("userB"));
    status.setSubscriptionRef(vmRequest.getSubscriptionIdentifier());
    status.setStatus(Boolean.TRUE);
    response.getResponseStatus().add(status);

    _manager.handleSubscriptionResponse(response);

    SubscriptionId subId = new SubscriptionId("userB", "subId");
    Mockito.verify(_subscriptionManager).upgradePendingSubscription(response,
        status, subId, ESiriModuleType.VEHICLE_MONITORING, request);
  }

  /**
   * Initiate a pending subscription and then simulate a timeout in the response
   * from the SIRI endpoint.
   */
  @Test
  public void testTimeout() throws InterruptedException {

    SubscriptionId subId = new SubscriptionId("userA", "subId");

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    _manager.registerPendingSubscription(request, subscriptionRequest);

    Mockito.verify(_subscriptionManager).getModuleTypeForSubscriptionId(subId);

    ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
    Mockito.verify(_schedulingService).scheduleResponseTimeoutTask(
        argument.capture());

    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);

    /**
     * We execute the timeout task ourselves. Normally it would be executed at
     * some point in the future by the scheduling service.
     */
    Runnable task = argument.getValue();
    task.run();

    /**
     * The timeout task should automatically attempt to reconnect if applicable
     */
    Mockito.verify(_client).handleRequestReconnectIfApplicable(request);

    /**
     * It should also clear out the pending subscription
     */
    assertFalse(_manager.isSubscriptionPending(subId));

    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);
  }

  /**
   * Initiate a pending subscription and then simulate a that the subscription
   * request was denied by the remote SIRI endpoint.
   */
  @Test
  public void testSubscriptionRequestAndInvalidResponse() {

    SubscriptionId subId = new SubscriptionId("userA", "subId");

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    _manager.registerPendingSubscription(request, subscriptionRequest);

    Mockito.verify(_subscriptionManager).getModuleTypeForSubscriptionId(subId);

    Mockito.verify(_schedulingService).scheduleResponseTimeoutTask(
        Mockito.any(Runnable.class));

    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();

    StatusResponseStructure status = new StatusResponseStructure();
    status.setSubscriberRef(SiriTypeFactory.particpantRef("userA"));
    status.setSubscriptionRef(vmRequest.getSubscriptionIdentifier());
    status.setStatus(Boolean.FALSE);
    response.getResponseStatus().add(status);

    _manager.handleSubscriptionResponse(response);

    assertFalse(_manager.isSubscriptionPending(subId));

    /**
     * There should be no side effects to others classes if the subscription
     * response indicates the subscription was not successful.
     */
    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);
  }

  /**
   * Initiate a pending subscription that overlaps with an existing subscription
   * of a different SIRI type. This should cause the subscription request to be
   * rejected and an exception to be thrown.
   */
  @Test
  public void testSubscriptionThatDuplicatesExistingSubscription() {

    SubscriptionId subId = new SubscriptionId("userA", "subId");

    Mockito.when(_subscriptionManager.getModuleTypeForSubscriptionId(subId)).thenReturn(
        ESiriModuleType.SITUATION_EXCHANGE);

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    try {
      _manager.registerPendingSubscription(request, subscriptionRequest);
      fail();

    } catch (SiriSubscriptionModuleTypeConflictException ex) {

      assertEquals(subId, ex.getSubscriptionId());
      assertEquals(ESiriModuleType.SITUATION_EXCHANGE,
          ex.getExistingModuleType());
      assertEquals(ESiriModuleType.VEHICLE_MONITORING,
          ex.getPendingModuleType());
    }

    Mockito.verify(_subscriptionManager).getModuleTypeForSubscriptionId(subId);

    /**
     * There should be no side effects to others classes if the subscription
     * response indicates the subscription was not successful.
     */
    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);

    assertFalse(_manager.isSubscriptionPending(subId));
  }

  /**
   * Initiate a pending subscription that includes to module subscriptions of
   * different types, but with the same id. This should cause the subscription
   * request to be rejected and an exception to be thrown.
   */
  @Test
  public void testSubscriptionThatContainsDuplicateSubscriptionsFromDifferentModules() {

    SubscriptionId subId = new SubscriptionId("userA", "subId");

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    SituationExchangeSubscriptionStructure sxRequest = new SituationExchangeSubscriptionStructure();
    sxRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getSituationExchangeSubscriptionRequest().add(sxRequest);

    try {
      _manager.registerPendingSubscription(request, subscriptionRequest);
      fail();

    } catch (SiriSubscriptionModuleTypeConflictException ex) {

      /**
       * The order of existing vs pending is determined by the order of values
       * in ESiriModuleType
       */
      assertEquals(subId, ex.getSubscriptionId());
      assertEquals(ESiriModuleType.VEHICLE_MONITORING,
          ex.getExistingModuleType());
      assertEquals(ESiriModuleType.SITUATION_EXCHANGE,
          ex.getPendingModuleType());
    }

    Mockito.verify(_subscriptionManager, Mockito.times(2)).getModuleTypeForSubscriptionId(
        subId);

    /**
     * There should be no side effects to others classes if the subscription
     * response indicates the subscription was not successful.
     */
    Mockito.verifyNoMoreInteractions(_subscriptionManager, _client,
        _schedulingService);

    assertFalse(_manager.isSubscriptionPending(subId));
  }
}
