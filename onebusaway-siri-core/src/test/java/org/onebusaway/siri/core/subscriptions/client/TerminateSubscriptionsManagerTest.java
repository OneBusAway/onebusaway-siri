/**
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

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SchedulingService;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

public class TerminateSubscriptionsManagerTest {

  private TerminateSubscriptionsManager _manager;
  private SiriClientHandler _client;
  private SchedulingService _schedulingService;
  private SiriClientSubscriptionManager _subscriptionManager;

  @Before
  public void setup() {

    _manager = new TerminateSubscriptionsManager();

    _client = Mockito.mock(SiriClientHandler.class);
    _manager.setClient(_client);

    _schedulingService = Mockito.mock(SchedulingService.class);
    _manager.setScheduleService(_schedulingService);

    _subscriptionManager = Mockito.mock(SiriClientSubscriptionManager.class);
    _manager.setSubscriptionManager(_subscriptionManager);
  }

  @Test
  public void testTerminateSubscription() {

    ClientSubscriptionInstance instance = createSubscriptionInstance();

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutFuture = Mockito.mock(ScheduledFuture.class);
    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(Mockito.any(Runnable.class))).thenReturn(
        timeoutFuture);

    String messageId = _manager.requestTerminationOfSubscription(instance,
        false);

    assertTrue(_manager.isTerminationOfSubscriptionPending(messageId));

    ArgumentCaptor<SiriClientRequest> requestArgument = ArgumentCaptor.forClass(SiriClientRequest.class);
    Mockito.verify(_client).handleRequest(requestArgument.capture());

    SiriClientRequest terminationRequest = requestArgument.getValue();
    assertEquals("http://localhost/", terminationRequest.getTargetUrl());

    Siri siri = terminationRequest.getPayload();
    TerminateSubscriptionRequestStructure terminateSubscriptionRequest = siri.getTerminateSubscriptionRequest();
    assertEquals("subscriberA",
        terminateSubscriptionRequest.getSubscriberRef().getValue());

    List<SubscriptionQualifierStructure> subscriptionRefs = terminateSubscriptionRequest.getSubscriptionRef();
    assertEquals(1, subscriptionRefs.size());
    assertEquals("subscriptionB", subscriptionRefs.get(0).getValue());

    /**
     * Verify that a response timeout task is registered
     */
    Mockito.verify(_schedulingService).scheduleResponseTimeoutTask(
        Mockito.any(Runnable.class));

    Mockito.verifyNoMoreInteractions(_client, _schedulingService,
        _subscriptionManager);

    TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
    response.setRequestMessageRef(SiriTypeFactory.messageId(messageId));

    List<TerminationResponseStatus> statuses = response.getTerminationResponseStatus();
    TerminationResponseStatus status = new TerminationResponseStatus();
    status.setStatus(Boolean.TRUE);
    status.setSubscriberRef(SiriTypeFactory.particpantRef("subscriberA"));
    status.setSubscriptionRef(SiriTypeFactory.subscriptionId("subscriptionB"));
    statuses.add(status);

    _manager.handleTerminateSubscriptionResponse(response);

    assertFalse(_manager.isTerminationOfSubscriptionPending(messageId));

    /**
     * Verify that the subscription is removed
     */
    Mockito.verify(_subscriptionManager).removeSubscription(
        instance.getSubscriptionId());

    /**
     * Verify that the timeout task is canceled
     */
    Mockito.verify(timeoutFuture).cancel(true);

    Mockito.verifyNoMoreInteractions(_client, _schedulingService,
        _subscriptionManager);
  }

  @Test
  public void testTerminateSubscriptionWithResubscribe() {

    ClientSubscriptionInstance instance = createSubscriptionInstance();

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutFuture = Mockito.mock(ScheduledFuture.class);
    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(Mockito.any(Runnable.class))).thenReturn(
        timeoutFuture);

    String messageId = _manager.requestTerminationOfSubscription(instance, true);

    TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
    response.setRequestMessageRef(SiriTypeFactory.messageId(messageId));

    List<TerminationResponseStatus> statuses = response.getTerminationResponseStatus();
    TerminationResponseStatus status = new TerminationResponseStatus();
    status.setStatus(Boolean.TRUE);
    status.setSubscriberRef(SiriTypeFactory.particpantRef("subscriberA"));
    status.setSubscriptionRef(SiriTypeFactory.subscriptionId("subscriptionB"));
    statuses.add(status);

    _manager.handleTerminateSubscriptionResponse(response);

    Mockito.verify(_client).handleRequest(instance.getRequest());
  }

  @Test
  public void testTerminateSubscriptionWithTimeout() {

    ClientSubscriptionInstance instance = createSubscriptionInstance();

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutFuture = Mockito.mock(ScheduledFuture.class);
    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(Mockito.any(Runnable.class))).thenReturn(
        timeoutFuture);

    String messageId = _manager.requestTerminationOfSubscription(instance,
        false);

    assertTrue(_manager.isTerminationOfSubscriptionPending(messageId));

    ArgumentCaptor<Runnable> timeoutTaskArgument = ArgumentCaptor.forClass(Runnable.class);

    /**
     * Verify that a response timeout task is registered
     */
    Mockito.verify(_schedulingService).scheduleResponseTimeoutTask(
        timeoutTaskArgument.capture());

    Runnable timeoutTask = timeoutTaskArgument.getValue();

    timeoutTask.run();

    assertFalse(_manager.isTerminationOfSubscriptionPending(messageId));

    /**
     * Verify that the subscription is removed
     */
    Mockito.verify(_subscriptionManager).removeSubscription(
        instance.getSubscriptionId());
  }
  
  @Test
  public void testTerminateSubscriptionWithTimeoutAndResubscribe() {

    ClientSubscriptionInstance instance = createSubscriptionInstance();

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutFuture = Mockito.mock(ScheduledFuture.class);
    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(Mockito.any(Runnable.class))).thenReturn(
        timeoutFuture);

    String messageId = _manager.requestTerminationOfSubscription(instance,
        true);

    assertTrue(_manager.isTerminationOfSubscriptionPending(messageId));

    ArgumentCaptor<Runnable> timeoutTaskArgument = ArgumentCaptor.forClass(Runnable.class);

    /**
     * Verify that a response timeout task is registered
     */
    Mockito.verify(_schedulingService).scheduleResponseTimeoutTask(
        timeoutTaskArgument.capture());

    Runnable timeoutTask = timeoutTaskArgument.getValue();

    timeoutTask.run();

    assertFalse(_manager.isTerminationOfSubscriptionPending(messageId));

    /**
     * Verify that the subscription is removed
     */
    Mockito.verify(_subscriptionManager).removeSubscription(
        instance.getSubscriptionId());
    
    Mockito.verify(_client).handleRequest(instance.getRequest());
  }

  private ClientSubscriptionInstance createSubscriptionInstance() {
    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost/", ESiriVersion.V1_3);
    SubscriptionId subscriptionId = new SubscriptionId("subscriberA",
        "subscriptionB");
    SiriClientRequest request = new SiriClientRequest();
    ESiriModuleType moduleType = ESiriModuleType.VEHICLE_MONITORING;
    ScheduledFuture<?> expirationTask = Mockito.mock(ScheduledFuture.class);

    ClientSubscriptionInstance instance = new ClientSubscriptionInstance(
        channel, subscriptionId, request, moduleType, expirationTask);
    return instance;
  }
}
