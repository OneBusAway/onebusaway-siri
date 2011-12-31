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
import static org.junit.Assert.assertSame;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.versioning.ESiriVersion;

public class HeartbeatManagerTest {

  private HeartbeatManager _manager;

  private SchedulingService _schedulingService;

  private SiriClientSubscriptionManager _subscriptionManager;

  @Before
  public void setup() {
    _manager = new HeartbeatManager();

    _schedulingService = Mockito.mock(SchedulingService.class);
    _manager.setSchedulingService(_schedulingService);

    _subscriptionManager = Mockito.mock(SiriClientSubscriptionManager.class);
    _manager.setSubscriptionManager(_subscriptionManager);
  }

  @Test
  public void testResetHeartbeat() {

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost/", ESiriVersion.V1_3);

    Mockito.when(_schedulingService.getResponseTimeout()).thenReturn(10);

    _manager.resetHeartbeat(channel, 30);

    assertEquals(30, channel.getHeartbeatInterval());

    /**
     * The heartbeat task is schedule for the hearbeat interval (30 secs) + the
     * response timeout (10 secs).
     */
    Mockito.verify(_schedulingService).schedule(Mockito.any(Runnable.class),
        Mockito.eq(40L), Mockito.eq(TimeUnit.SECONDS));
  }

  @Test
  public void testResetHeartbeatToZero() {

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost/", ESiriVersion.V1_3);

    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> future = Mockito.mock(ScheduledFuture.class);
    Mockito.when(
        _schedulingService.schedule(Mockito.any(Runnable.class),
            Mockito.eq(30L), Mockito.eq(TimeUnit.SECONDS))).thenReturn(future);

    _manager.resetHeartbeat(channel, 30);

    assertSame(future, channel.getHeartbeatTask());

    Mockito.verify(_schedulingService).getResponseTimeout();
    Mockito.verify(_schedulingService).schedule(Mockito.any(Runnable.class),
        Mockito.eq(30L), Mockito.eq(TimeUnit.SECONDS));

    _manager.resetHeartbeat(channel, 0);

    Mockito.verify(future).cancel(true);
    Mockito.verifyNoMoreInteractions(_schedulingService);
  }

  @Test
  public void testResetHeartbeatTimeoutTask() {

    ClientSubscriptionChannel channel = new ClientSubscriptionChannel(
        "http://localhost/", ESiriVersion.V1_3);

    _manager.resetHeartbeat(channel, 30);

    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    Mockito.verify(_schedulingService).schedule(captor.capture(),
        Mockito.eq(30L), Mockito.eq(TimeUnit.SECONDS));

    Runnable timeoutTask = captor.getValue();
    timeoutTask.run();

    Mockito.verify(_subscriptionManager).handleChannelDisconnectAndReconnect(
        channel);
  }
}
