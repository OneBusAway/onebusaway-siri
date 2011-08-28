/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2011 Google, Inc.
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
package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionManager;

/**
 * Listener for {@link SiriServerSubscriptionManager} subscription events.
 * 
 * @author bdferris
 * @see SiriServerSubscriptionManager#addListener(SiriSubscriptionManagerListener)
 */
public interface SiriSubscriptionManagerListener {

  /**
   * Called whenever a new subscription is established
   * 
   * @param manager the subscription manager
   */
  public void subscriptionAdded(SiriServerSubscriptionManager manager);

  /**
   * Called whenever a subscription is terminated
   * 
   * @param manager
   */
  public void subscriptionRemoved(SiriServerSubscriptionManager manager);
}
