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
package org.onebusaway.siri.core;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface SchedulingService {

  /**
   * Timeout, in seconds, in which we expect to receive a response for a pending
   * request if it's received asynchronously (ex SubscriptionRequest =>
   * SubscriptionResponse).
   */
  public int getResponseTimeout();

  /**
   * Timeout, in seconds, in which we expect to receive a response for a pending
   * request if it's received asynchronously (ex SubscriptionRequest =>
   * SubscriptionResponse).
   * 
   * @param responseTimeout timeout, in seconds
   */
  public void setResponseTimeout(int responseTimeout);

  public Future<?> submit(Runnable task);

  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
      long initialDelay, long period, TimeUnit unit);

  public ScheduledFuture<?> scheduleResponseTimeoutTask(Runnable task);

}
