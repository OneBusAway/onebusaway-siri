/**
 * Copyright (C) 2011 Google, Inc
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.HeartbeatNotificationStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;

public class SiriClientSubscriptionManagerTest {

  private SiriClientSubscriptionManager _manager;

  private CheckStatusManager _checkStatusManager;

  private HeartbeatManager _heartbeatManager;

  private InitiateSubscriptionsManager _initiateSubscriptionsManager;

  private TerminateSubscriptionsManager _terminateSubscriptionsManager;

  private SchedulingService _schedulingService;

  @Before
  public void before() {
    _manager = new SiriClientSubscriptionManager();

    _checkStatusManager = Mockito.mock(CheckStatusManager.class);
    _manager.setCheckStatusManager(_checkStatusManager);

    _heartbeatManager = Mockito.mock(HeartbeatManager.class);
    _manager.setHeartbeatManager(_heartbeatManager);

    _initiateSubscriptionsManager = Mockito.mock(InitiateSubscriptionsManager.class);
    _manager.setInitiateSubscriptionManager(_initiateSubscriptionsManager);

    _terminateSubscriptionsManager = Mockito.mock(TerminateSubscriptionsManager.class);
    _manager.setTerminateSubscriptionsManager(_terminateSubscriptionsManager);

    _schedulingService = Mockito.mock(SchedulingService.class);
    _manager.setSchedulingService(_schedulingService);
  }

  @Test
  public void testClearPendingSubscription() {
    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    _manager.clearPendingSubscription(request, subscriptionRequest);
    Mockito.verify(_initiateSubscriptionsManager).clearPendingSubscription(
        request, subscriptionRequest);
  }

  @Test
  public void testGetChannelInfoForServiceDelivery() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setChannelContext("CONTEXT");

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    ServiceDelivery serviceDelivery = new ServiceDelivery();
    serviceDelivery.setAddress("http://localhost/");

    SiriChannelInfo info = _manager.getChannelInfoForServiceDelivery(serviceDelivery);

    assertEquals("CONTEXT", info.getContext());
  }

  @Test
  public void testGetModuleTypeForSubscriptionId() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    assertEquals(moduleType,
        _manager.getModuleTypeForSubscriptionId(subscriptionId));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testHandleChannelDisconnectAndReconnect() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost/", ESiriVersion.V1_3);
    channel.getSubscriptions().add(subscriptionId);

    _manager.handleChannelDisconnectAndReconnect(channel);

    Mockito.verify(_terminateSubscriptionsManager).requestTerminationOfSubscriptions(
        Mockito.anyList(), Mockito.eq(true));
  }

  @Test
  public void testHandleCheckStatusNotification() {
    CheckStatusResponseStructure response = new CheckStatusResponseStructure();
    _manager.handleCheckStatusNotification(response);
    Mockito.verify(_checkStatusManager).handleCheckStatusResponse(response);
  }

  @Test
  public void testHandleHeartbeatNotification() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setHeartbeatInterval(30);

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    HeartbeatNotificationStructure heartbeat = new HeartbeatNotificationStructure();
    heartbeat.setAddress("http://localhost/");
    _manager.handleHeartbeatNotification(heartbeat);

    Mockito.verify(_heartbeatManager).resetHeartbeat(
        Mockito.any(ClientSubscriptionChannel.class), Mockito.eq(30));
  }

  @Test
  public void testHandleSubscriptionResponse() {
    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    _manager.handleSubscriptionResponse(response);
    Mockito.verify(_initiateSubscriptionsManager).handleSubscriptionResponse(
        response);
  }

  @Test
  public void testHandleTerminateSubscriptionResponse() {
    TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
    _manager.handleTerminateSubscriptionResponse(response);
    Mockito.verify(_terminateSubscriptionsManager).handleTerminateSubscriptionResponse(
        response);
  }

  @Test
  public void testIsSubscriptionActiveForModuleDelivery() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    AbstractServiceDeliveryStructure moduleDelivery = new AbstractServiceDeliveryStructure();
    moduleDelivery.setSubscriberRef(SiriTypeFactory.particpantRef("subscriberA"));
    moduleDelivery.setSubscriptionRef(SiriTypeFactory.subscriptionId("subscriptionB"));
    _manager.isSubscriptionActiveForModuleDelivery(moduleDelivery);
  }

  @Test
  public void testRegisterPendingSubscription() {
    SiriClientRequest request = new SiriClientRequest();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    _manager.registerPendingSubscription(request, subscriptionRequest);
    Mockito.verify(_initiateSubscriptionsManager).registerPendingSubscription(
        request, subscriptionRequest);
  }

  @Test
  public void testRemoveSubscription() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setInitialTerminationDuration(60 * 1000);

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> expirationTask = Mockito.mock(ScheduledFuture.class);
    Mockito.when(
        _schedulingService.schedule(Mockito.any(Runnable.class),
            Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(
        expirationTask);

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    _manager.removeSubscription(subscriptionId);

    assertFalse(_manager.isSubscriptionActive(subscriptionId));

    /**
     * This technically gets called twice. Once for establishing the
     * subscription and once for removing it.
     */
    Mockito.verify(_checkStatusManager, Mockito.times(2)).resetCheckStatusTask(
        Mockito.any(ClientSubscriptionChannel.class), Mockito.eq(0));

    Mockito.verify(_heartbeatManager, Mockito.times(2)).resetHeartbeat(
        Mockito.any(ClientSubscriptionChannel.class), Mockito.eq(0));

    /**
     * Make sure the expiration task is cancelled
     */
    Mockito.verify(expirationTask).cancel(true);
  }

  @Test
  public void testRequestSubscriptionTerminationAndResubscription() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    _manager.requestSubscriptionTerminationAndResubscription(subscriptionId);

    Mockito.verify(_terminateSubscriptionsManager).requestTerminationOfSubscription(
        Mockito.any(ClientSubscriptionInstance.class), Mockito.eq(true));
  }

  @Test
  public void testUpgradePendingSubscription() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    assertTrue(_manager.isSubscriptionActive(subscriptionId));
  }

  @Test
  public void testUpgradePendingSubscriptionWithExpirationTask() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setInitialTerminationDuration(60 * 1000);

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    /**
     * Verify that the expiration task is scheduled properly
     */
    Mockito.verify(_schedulingService).schedule(Mockito.any(Runnable.class),
        Mockito.eq(60 * 1000L), Mockito.eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void testUpgradePendingSubscriptionWithExpirationTaskAndResponseValidUntil() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    status.setValidUntil(new Date(System.currentTimeMillis() + 30 * 1000L));
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setInitialTerminationDuration(60 * 1000);

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    /**
     * Verify that the expiration task is scheduled properly
     */
    ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
    Mockito.verify(_schedulingService).schedule(Mockito.any(Runnable.class),
        timeoutCaptor.capture(), Mockito.eq(TimeUnit.MILLISECONDS));

    /**
     * The timeout is set relative to System.currentTimeMillis(), so we can't
     * compare it exactly
     */
    long timeout = timeoutCaptor.getValue();
    long delta = Math.abs(30 * 1000L - timeout);
    assertTrue(delta < 150);
  }

  @Test
  public void testExpirationTask() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setInitialTerminationDuration(60 * 1000);

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    ArgumentCaptor<Runnable> expirationTaskCaptor = ArgumentCaptor.forClass(Runnable.class);
    Mockito.verify(_schedulingService).schedule(expirationTaskCaptor.capture(),
        Mockito.eq(60 * 1000L), Mockito.eq(TimeUnit.MILLISECONDS));

    Runnable expirationTask = expirationTaskCaptor.getValue();

    expirationTask.run();

    Mockito.verify(_terminateSubscriptionsManager).requestTerminationOfSubscription(
        Mockito.any(ClientSubscriptionInstance.class), Mockito.eq(true));
  }

  @Test
  public void testCheckStatusTask() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setCheckStatusInterval(30);

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    /**
     * Verify that the expiration task is scheduled properly
     */
    ArgumentCaptor<ClientSubscriptionChannel> captor = ArgumentCaptor.forClass(ClientSubscriptionChannel.class);
    Mockito.verify(_checkStatusManager).resetCheckStatusTask(captor.capture(),
        Mockito.eq(30));

    ClientSubscriptionChannel channel = captor.getValue();
    assertNull(channel.getCheckStatusUrl());
  }

  @Test
  public void testCheckStatusTaskWithCustomUrl() {

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure status = new StatusResponseStructure();
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    SiriClientRequest originalSubscriptionRequest = new SiriClientRequest();
    originalSubscriptionRequest.setTargetUrl("http://localhost/");
    originalSubscriptionRequest.setCheckStatusInterval(30);
    originalSubscriptionRequest.setCheckStatusUrl("http://localhost/checkStatus");

    _manager.upgradePendingSubscription(response, status, subscriptionId,
        moduleType, originalSubscriptionRequest);

    ArgumentCaptor<ClientSubscriptionChannel> captor = ArgumentCaptor.forClass(ClientSubscriptionChannel.class);
    Mockito.verify(_checkStatusManager).resetCheckStatusTask(captor.capture(),
        Mockito.eq(30));

    ClientSubscriptionChannel channel = captor.getValue();
    assertEquals("http://localhost/checkStatus", channel.getCheckStatusUrl());
  }
}
