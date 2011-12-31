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
package org.onebusaway.siri.core.services;

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

  public <T> ScheduledFuture<T> schedule(Runnable command, long delay,
      TimeUnit unit);

  public <T> ScheduledFuture<T> scheduleAtFixedRate(Runnable command,
      long initialDelay, long period, TimeUnit unit);

  /**
   * Schedule a task to run in N seconds, where N is the response timeout
   * interval as returned by {@link #getResponseTimeout()}. This method is
   * useful for configuring a task to run if no response is received from some
   * remote endpoint in the response-timeout-interval. If a response IS
   * received, the timeout task can be canceled using the returned
   * {@link ScheduledFuture} object.
   * 
   * @param <T>
   * @param task
   * @return
   */
  public <T> ScheduledFuture<T> scheduleResponseTimeoutTask(Runnable task);

}
