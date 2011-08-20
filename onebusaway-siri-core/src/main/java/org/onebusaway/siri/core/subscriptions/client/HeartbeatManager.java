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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.siri.core.SchedulingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class HeartbeatManager {

  private static final Logger _log = LoggerFactory.getLogger(HeartbeatManager.class);

  private SchedulingService _schedulingService;

  private SiriClientSubscriptionManager _subscriptionManager;

  @Inject
  public void setSchedulingService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  @Inject
  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  /**
   * 
   * @param channel
   * @param heartbeatInterval how often a heartbeat notification is expected, in
   *          seconds
   */
  public void resetHeartbeat(ClientSubscriptionChannel channel,
      int heartbeatInterval) {

    synchronized (channel) {

      channel.setHeartbeatInterval(heartbeatInterval);

      ScheduledFuture<?> heartbeatTask = channel.getHeartbeatTask();
      if (heartbeatTask != null) {
        heartbeatTask.cancel(true);
        channel.setHeartbeatTask(null);
      }

      if (heartbeatInterval > 0) {
        HeartbeatTimeoutTask task = new HeartbeatTimeoutTask(
            _subscriptionManager, channel);

        /**
         * Note that we add the response timeout to the heartbeat interval to
         * add a little wiggle room in case the response is delayed
         */
        long interval = heartbeatInterval
            + _schedulingService.getResponseTimeout();
        heartbeatTask = _schedulingService.schedule(task, interval,
            TimeUnit.SECONDS);
        channel.setHeartbeatTask(heartbeatTask);
      }
    }
  }

  private static class HeartbeatTimeoutTask implements Runnable {

    private final SiriClientSubscriptionManager manager;

    private final ClientSubscriptionChannel channel;

    public HeartbeatTimeoutTask(SiriClientSubscriptionManager manager,
        ClientSubscriptionChannel channel) {
      this.manager = manager;
      this.channel = channel;
    }

    @Override
    public void run() {
      _log.warn("heartbeat interval timeout: " + channel.getAddress());
      manager.handleChannelDisconnectAndReconnect(channel);
    }
  }

}
