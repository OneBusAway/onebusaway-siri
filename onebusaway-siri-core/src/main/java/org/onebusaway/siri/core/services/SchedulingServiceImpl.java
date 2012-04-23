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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

@Singleton
class SchedulingServiceImpl implements SchedulingService {

  /**
   * For executing one-time tasks.
   */
  private ExecutorService _executor = null;

  /**
   * For executing recurring tasks.
   */
  private ScheduledExecutorService _scheduledExecutor = null;

  /**
   * Timeout, in seconds, in which we expect to receive a response for a pending
   * request if it's received asynchronously (ex SubscriptionRequest =>
   * SubscriptionResponse).
   */
  private int _responseTimeout = 10;

  @PostConstruct
  public void start() {
    _executor = Executors.newCachedThreadPool();
    _scheduledExecutor = Executors.newScheduledThreadPool(1);
  }

  @PreDestroy
  public void stop() {
    if (_executor != null) {
      _executor.shutdownNow();
    }
    if (_scheduledExecutor != null) {
      _scheduledExecutor.shutdownNow();
    }
  }

  /****
   * {@link SchedulingService}
   ****/

  /**
   * See {@link SchedulingService#getResponseTimeout()}
   */
  @Override
  public int getResponseTimeout() {
    return _responseTimeout;
  }

  /**
   * See {@link SchedulingService#setResponseTimeout(int)}
   */
  public void setResponseTimeout(int responseTimeout) {
    _responseTimeout = responseTimeout;
  }

  @Override
  public Future<?> submit(Runnable task) {
    return _executor.submit(task);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> ScheduledFuture<T> schedule(Runnable command, long delay,
      TimeUnit unit) {
    return (ScheduledFuture<T>) _scheduledExecutor.schedule(command, delay,
        unit);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> ScheduledFuture<T> scheduleAtFixedRate(Runnable command,
      long initialDelay, long period, TimeUnit unit) {
    return (ScheduledFuture<T>) _scheduledExecutor.scheduleAtFixedRate(command,
        initialDelay, period, unit);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> ScheduledFuture<T> scheduleResponseTimeoutTask(Runnable task) {
    return (ScheduledFuture<T>) _scheduledExecutor.schedule(task,
        _responseTimeout, TimeUnit.SECONDS);
  }
}
