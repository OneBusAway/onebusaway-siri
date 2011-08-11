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
