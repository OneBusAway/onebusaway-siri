package org.onebusaway.siri.core;

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

  private ScheduledExecutorService _executor = null;

  /**
   * Timeout, in seconds, in which we expect to receive a response for a pending
   * request if it's received asynchronously (ex SubscriptionRequest =>
   * SubscriptionResponse).
   */
  private int _responseTimeout = 10;

  @PostConstruct
  public void start() {
    _executor = Executors.newSingleThreadScheduledExecutor();
  }

  @PreDestroy
  public void stop() {
    if (_executor != null)
      _executor.shutdownNow();
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

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return _executor.schedule(command, delay, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
      long initialDelay, long period, TimeUnit unit) {
    return _executor.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleResponseTimeoutTask(Runnable task) {
    return _executor.schedule(task, _responseTimeout, TimeUnit.SECONDS);
  }
}
