package org.onebusaway.siri.core;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;

/**
 * These are methods exposed internally by SIRI client so that helper classes
 * can interact with hte {@link SiriClient} without exposing methods that should
 * not be made public to external users of {@link SiriClient}.
 * 
 * @author bdferris
 * 
 */
interface PrivateSiriClient {

  public HttpResponse sendHttpRequest(String url, Object request);

  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);
}
