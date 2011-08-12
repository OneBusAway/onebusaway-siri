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

import org.onebusaway.siri.core.SiriClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This task is run when a {@link SiriClient} heartbeat times out, canceling the
 * active subscription for that connection.
 * 
 * @author bdferris
 */
class ClientHeartbeatTimeoutTask implements Runnable {

  private static final Logger _log = LoggerFactory.getLogger(ClientHeartbeatTimeoutTask.class);

  private final SiriClientSubscriptionManager manager;
  
  private final ClientSubscriptionChannel channel;

  public ClientHeartbeatTimeoutTask(SiriClientSubscriptionManager manager,
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