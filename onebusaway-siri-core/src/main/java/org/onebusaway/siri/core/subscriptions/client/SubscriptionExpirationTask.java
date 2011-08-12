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

import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionExpirationTask implements Runnable {

  private static final Logger _log = LoggerFactory.getLogger(SubscriptionExpirationTask.class);

  private final SiriClientSubscriptionManager _manager;

  private final SubscriptionId _id;

  public SubscriptionExpirationTask(SiriClientSubscriptionManager manager,
      SubscriptionId id) {
    _manager = manager;
    _id = id;
  }

  @Override
  public void run() {
    _log.debug("expiring subscription " + _id);
    _manager.requestSubscriptionTerminationAndResubscription(_id);
  }
}
