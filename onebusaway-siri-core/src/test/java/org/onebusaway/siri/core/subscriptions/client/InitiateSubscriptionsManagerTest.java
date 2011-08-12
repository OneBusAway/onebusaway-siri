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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SchedulingService;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

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

  @Test
  public void testSubscriptionRequestAndResponse() {

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    _manager.registerPendingSubscription(request, subscriptionRequest);

    Mockito.verify(_schedulingService).scheduleResponseTimeoutTask(
        Mockito.any(Runnable.class));

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();

    StatusResponseStructure status = new StatusResponseStructure();
    status.setSubscriberRef(SiriTypeFactory.particpantRef("userA"));
    status.setSubscriptionRef(vmRequest.getSubscriptionIdentifier());
    status.setStatus(Boolean.TRUE);
    response.getResponseStatus().add(status);

    _manager.handleSubscriptionResponse(response);

    SubscriptionId subId = new SubscriptionId("userA", "subId");
    Mockito.verify(_subscriptionManager).upgradePendingSubscription(response,
        status, subId, ESiriModuleType.VEHICLE_MONITORING, vmRequest, request);
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
        status, subId, ESiriModuleType.VEHICLE_MONITORING, vmRequest, request);
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
        status, subId, ESiriModuleType.VEHICLE_MONITORING, vmRequest, request);
  }

  @Test
  public void testTimeout() throws InterruptedException {

    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setRequestorRef(SiriTypeFactory.particpantRef("userA"));

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("subId"));
    subscriptionRequest.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    _manager.registerPendingSubscription(request, subscriptionRequest);

  }
}
