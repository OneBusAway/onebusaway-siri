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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.onebusaway.siri.core.SchedulingService;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.Siri;

public class CheckStatusManagerTest {

  private CheckStatusManager _checkStatusManager;

  private SchedulingService _schedulingService;

  private SiriClientSubscriptionManager _subscriptionManager;

  private SiriClientHandler _client;

  @Before
  public void setup() {
    _checkStatusManager = new CheckStatusManager();

    _schedulingService = Mockito.mock(SchedulingService.class);
    _checkStatusManager.setScheduleService(_schedulingService);

    _subscriptionManager = Mockito.mock(SiriClientSubscriptionManager.class);
    _checkStatusManager.setSubscriptionManager(_subscriptionManager);

    _client = Mockito.mock(SiriClientHandler.class);
    _checkStatusManager.setClient(_client);
  }

  @Test
  public void testResetCheckStatusTask() {

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost", ESiriVersion.V1_3);

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> taskMonitor = Mockito.mock(ScheduledFuture.class);

    Mockito.when(
        _schedulingService.scheduleAtFixedRate(Mockito.any(Runnable.class),
            Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(
        taskMonitor);

    _checkStatusManager.resetCheckStatusTask(channel, 30);

    assertSame(taskMonitor, channel.getCheckStatusTask());

    _checkStatusManager.resetCheckStatusTask(channel, 0);

    /**
     * Verify that the existing check status task is canceled when the task is
     * reset
     */
    Mockito.verify(taskMonitor).cancel(true);

    /**
     * Verify that no new task is created when the checkStatusInterval is 0
     */
    assertNull(channel.getCheckStatusTask());
  }

  @Test
  public void testCheckStatusTask() {

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost", ESiriVersion.V1_0);

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> taskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> taskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleAtFixedRate(taskArgument.capture(),
            Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(
        taskMonitor);

    _checkStatusManager.resetCheckStatusTask(channel, 30);

    /**
     * We capture the check status task so that we can fire it off on our own
     */
    Runnable task = taskArgument.getValue();
    assertNotNull(task);

    /**
     * Before we do, we're also going to capture the timeout task that gets
     * created when we initiate a CheckStatus request
     */
    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutTaskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> timeoutTaskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(timeoutTaskArgument.capture())).thenReturn(
        timeoutTaskMonitor);

    /**
     * Initiate the CheckStatus request
     */
    task.run();

    ArgumentCaptor<SiriClientRequest> requestArgument = ArgumentCaptor.forClass(SiriClientRequest.class);

    Mockito.verify(_client).handleRequest(requestArgument.capture());

    SiriClientRequest request = requestArgument.getValue();
    assertNotNull(request);

    assertEquals("http://localhost", request.getTargetUrl());
    assertEquals(ESiriVersion.V1_0, request.getTargetVersion());

    Siri siri = request.getPayload();
    CheckStatusRequestStructure checkStatusRequest = siri.getCheckStatusRequest();
    MessageQualifierStructure messageId = checkStatusRequest.getMessageIdentifier();
    assertNotNull(messageId);

    CheckStatusResponseStructure checkStatusResponse = new CheckStatusResponseStructure();
    checkStatusResponse.setRequestMessageRef(SiriTypeFactory.messageRef(messageId.getValue()));
    checkStatusResponse.setStatus(Boolean.TRUE);
    
    _checkStatusManager.handleCheckStatusResponse(checkStatusResponse);

    /**
     * Verify that the timeout task is canceled when a valid CheckStatusResponse
     * is received
     */
    Mockito.verify(timeoutTaskMonitor).cancel(true);
  }
  
  @Test
  public void testCheckStatusTaskWithBadResponse() {

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost", ESiriVersion.V1_0);

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> taskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> taskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleAtFixedRate(taskArgument.capture(),
            Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(
        taskMonitor);

    _checkStatusManager.resetCheckStatusTask(channel, 30);

    /**
     * We capture the check status task so that we can fire it off on our own
     */
    Runnable task = taskArgument.getValue();
    assertNotNull(task);

    /**
     * Before we do, we're also going to capture the timeout task that gets
     * created when we initiate a CheckStatus request
     */
    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutTaskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> timeoutTaskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(timeoutTaskArgument.capture())).thenReturn(
        timeoutTaskMonitor);

    /**
     * Initiate the CheckStatus request
     */
    task.run();

    ArgumentCaptor<SiriClientRequest> requestArgument = ArgumentCaptor.forClass(SiriClientRequest.class);

    Mockito.verify(_client).handleRequest(requestArgument.capture());

    SiriClientRequest request = requestArgument.getValue();
    assertNotNull(request);

    assertEquals("http://localhost", request.getTargetUrl());
    assertEquals(ESiriVersion.V1_0, request.getTargetVersion());

    Siri siri = request.getPayload();
    CheckStatusRequestStructure checkStatusRequest = siri.getCheckStatusRequest();
    MessageQualifierStructure messageId = checkStatusRequest.getMessageIdentifier();
    assertNotNull(messageId);

    CheckStatusResponseStructure checkStatusResponse = new CheckStatusResponseStructure();
    checkStatusResponse.setRequestMessageRef(SiriTypeFactory.messageRef(messageId.getValue()));
    checkStatusResponse.setStatus(Boolean.FALSE);

    _checkStatusManager.handleCheckStatusResponse(checkStatusResponse);

    /**
     * Verify that the timeout task is canceled when a valid CheckStatusResponse
     * is received
     */
    Mockito.verify(timeoutTaskMonitor).cancel(true);
    
    /**
     * Verify that the error causes a disconnect-reconnect
     */
    Mockito.verify(_subscriptionManager).handleChannelDisconnectAndReconnect(channel);
  }
  
  @Test
  public void testCheckStatusTaskWithServerTimestampChanged() {

    
    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost", ESiriVersion.V1_0);
    
    long now = System.currentTimeMillis();
    channel.setLastServiceStartedTime(new Date(now));
    
    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> taskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> taskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleAtFixedRate(taskArgument.capture(),
            Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(
        taskMonitor);

    _checkStatusManager.resetCheckStatusTask(channel, 30);

    /**
     * We capture the check status task so that we can fire it off on our own
     */
    Runnable task = taskArgument.getValue();
    assertNotNull(task);

    /**
     * Before we do, we're also going to capture the timeout task that gets
     * created when we initiate a CheckStatus request
     */
    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutTaskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> timeoutTaskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(timeoutTaskArgument.capture())).thenReturn(
        timeoutTaskMonitor);

    /**
     * Initiate the CheckStatus request
     */
    task.run();

    ArgumentCaptor<SiriClientRequest> requestArgument = ArgumentCaptor.forClass(SiriClientRequest.class);

    Mockito.verify(_client).handleRequest(requestArgument.capture());

    SiriClientRequest request = requestArgument.getValue();
    assertNotNull(request);

    assertEquals("http://localhost", request.getTargetUrl());
    assertEquals(ESiriVersion.V1_0, request.getTargetVersion());

    Siri siri = request.getPayload();
    CheckStatusRequestStructure checkStatusRequest = siri.getCheckStatusRequest();
    MessageQualifierStructure messageId = checkStatusRequest.getMessageIdentifier();
    assertNotNull(messageId);

    /**
     * In the response, we're going to bump the service started timestamp
     */
    CheckStatusResponseStructure checkStatusResponse = new CheckStatusResponseStructure();
    checkStatusResponse.setRequestMessageRef(SiriTypeFactory.messageRef(messageId.getValue()));
    checkStatusResponse.setStatus(Boolean.TRUE);
    checkStatusResponse.setServiceStartedTime(new Date(now+1000));

    _checkStatusManager.handleCheckStatusResponse(checkStatusResponse);

    /**
     * Verify that the timeout task is canceled when a valid CheckStatusResponse
     * is received
     */
    Mockito.verify(timeoutTaskMonitor).cancel(true);
    
    /**
     * Verify that the error causes a disconnect-reconnect
     */
    Mockito.verify(_subscriptionManager).handleChannelDisconnectAndReconnect(channel);
  }
  
  @Test
  public void testCheckStatusTaskWithResponseTimeout() {

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost", ESiriVersion.V1_0);

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> taskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> taskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleAtFixedRate(taskArgument.capture(),
            Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(
        taskMonitor);

    _checkStatusManager.resetCheckStatusTask(channel, 30);

    /**
     * We capture the check status task so that we can fire it off on our own
     */
    Runnable task = taskArgument.getValue();
    assertNotNull(task);

    /**
     * Before we do, we're also going to capture the timeout task that gets
     * created when we initiate a CheckStatus request
     */
    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> timeoutTaskMonitor = Mockito.mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> timeoutTaskArgument = ArgumentCaptor.forClass(Runnable.class);

    Mockito.when(
        _schedulingService.scheduleResponseTimeoutTask(timeoutTaskArgument.capture())).thenReturn(
        timeoutTaskMonitor);

    /**
     * Initiate the CheckStatus request
     */
    task.run();

    Mockito.verify(_client).handleRequest(Mockito.any(SiriClientRequest.class));

    /**
     * Now fire off the time-out task
     */
    Runnable timeoutTask = timeoutTaskArgument.getValue();    
    timeoutTask.run();

    Mockito.verify(_subscriptionManager).handleChannelDisconnectAndReconnect(channel);
  }
}
