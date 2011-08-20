/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.siri.core.SchedulingService;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.exceptions.SiriMissingArgumentException;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.MessageRefStructure;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.CheckStatusResponseBodyStructure.ErrorCondition;

/**
 * The CheckStatusManager is responsible for sending CheckStatusRequests to SIRI
 * endpoints, listening for responses, and taking appropriate reaction to reset
 * a subscription if the proper response is not received.
 * 
 * @author bdferris
 * 
 */
@Singleton
class CheckStatusManager {

  private static final Logger _log = LoggerFactory.getLogger(CheckStatusManager.class);

  private ConcurrentMap<String, PendingCheckStatusRequest> _pendingCheckStatusRequests = new ConcurrentHashMap<String, PendingCheckStatusRequest>();

  private SiriClientSubscriptionManager _subscriptionManager;

  private SiriClientHandler _client;

  private SchedulingService _schedulingService;

  @Inject
  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  @Inject
  public void setClient(SiriClientHandler client) {
    _client = client;
  }

  @Inject
  public void setScheduleService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  /**
   * Reset the CheckStatus task for the specified channel. Any existing check
   * status task will be canceled and cleared. If the checkStatusInterval is
   * greater than zero, then a new check status task will be created and
   * scheduled.
   * 
   * @param channel the CheckStatus task will be reset on this channel
   * @param checkStatusInterval the interval between check status requests, in
   *          seconds, or zero, to indicate no check status requests should be
   *          sent.
   */
  public void resetCheckStatusTask(ClientSubscriptionChannel channel,
      int checkStatusInterval) {

    synchronized (channel) {

      channel.setCheckStatusInterval(checkStatusInterval);

      _log.debug("resetting check status task: channel={} interval={}",
          channel.getAddress(), checkStatusInterval);

      ScheduledFuture<?> checkStatusTask = channel.getCheckStatusTask();
      if (checkStatusTask != null) {
        checkStatusTask.cancel(true);
        channel.setCheckStatusTask(null);
      }

      if (checkStatusInterval > 0) {
        CheckStatusTask task = new CheckStatusTask(channel);

        checkStatusTask = _schedulingService.scheduleAtFixedRate(task,
            checkStatusInterval, checkStatusInterval, TimeUnit.SECONDS);
        channel.setCheckStatusTask(checkStatusTask);
      }
    }
  }

  /**
   * Submit a CheckStatusResponse received from a SIRI endpoint. The
   * subscription will be reset if the CheckStatusResponse has errors or if the
   * SIRI endpoint has been restarted since the most-recent check.
   * 
   * @param response the CheckStatusResponse
   */
  public void handleCheckStatusResponse(CheckStatusResponseStructure response) {

    _log.debug("handle check status response");

    MessageRefStructure messageRef = response.getRequestMessageRef();

    if (messageRef == null || messageRef.getValue() == null)
      throw new SiriMissingArgumentException("RequestMessageRef");

    PendingCheckStatusRequest pending = _pendingCheckStatusRequests.remove(messageRef.getValue());

    if (pending == null) {
      _log.warn("Pending CheckStatus channel not found for messageId="
          + messageRef.getValue());
      return;
    }

    /**
     * Cancel the timeout task
     */
    ScheduledFuture<?> task = pending.getTimeoutTask();
    task.cancel(true);

    ClientSubscriptionChannel channel = pending.getChannel();

    boolean isNewer = isCheckStatusNewer(channel, response);
    boolean isInError = response.isStatus() == null || !response.isStatus();

    /**
     * If the channel hasn't been rebooted (aka it's not newer) and the channel
     * is not in error, we're cool!
     */
    if (!(isNewer || isInError))
      return;

    logErrorInCheckStatusResponse(channel, response, isNewer, isInError);

    _subscriptionManager.handleChannelDisconnectAndReconnect(channel);
  }

  /****
   * Private Methods
   ****/

  private void checkStatus(ClientSubscriptionChannel channel) {

    MessageQualifierStructure messageId = SiriTypeFactory.randomMessageId();

    CheckStatusRequestStructure checkStatus = new CheckStatusRequestStructure();
    checkStatus.setRequestTimestamp(new Date());
    checkStatus.setMessageIdentifier(messageId);

    Siri siri = new Siri();
    siri.setCheckStatusRequest(checkStatus);

    String url = channel.getCheckStatusUrl();
    if (url == null)
      url = channel.getAddress();

    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl(url);
    request.setTargetVersion(channel.getTargetVersion());
    request.setPayload(siri);

    PendingCheckStatusTimeoutTask timeoutTask = new PendingCheckStatusTimeoutTask(
        channel, messageId.getValue());
    ScheduledFuture<?> future = _schedulingService.scheduleResponseTimeoutTask(timeoutTask);

    /**
     * 
     */
    PendingCheckStatusRequest pending = new PendingCheckStatusRequest(channel,
        future);
    _pendingCheckStatusRequests.put(messageId.getValue(), pending);

    _log.debug("sending check status request for channel={} messageId={}",
        channel.getAddress(), messageId.getValue());

    /**
     * Submit the request
     */
    _client.handleRequest(request);
  }

  private boolean isCheckStatusNewer(ClientSubscriptionChannel channel,
      CheckStatusResponseStructure response) {

    Date serviceStartedTime = response.getServiceStartedTime();

    if (serviceStartedTime == null)
      return false;

    /**
     * Has the service start time been adjusted since our last status check?
     */

    Date lastServiceStartedTime = channel.getLastServiceStartedTime();

    if (lastServiceStartedTime == null) {
      channel.setLastServiceStartedTime(serviceStartedTime);
    } else if (serviceStartedTime.after(lastServiceStartedTime)) {
      return true;
    }

    return false;
  }

  public void logErrorInCheckStatusResponse(ClientSubscriptionChannel channel,
      CheckStatusResponseStructure response, boolean isNewer, boolean isInError) {

    StringBuilder b = new StringBuilder();
    b.append("check status failed for channel:");
    b.append(" address=").append(channel.getAddress());

    if (isNewer) {
      b.append(" prevServiceStartedTime=");
      b.append(channel.getLastServiceStartedTime());
      b.append(" newServiceStartedTime=");
      b.append(response.getServiceStartedTime());
    }

    ErrorCondition error = response.getErrorCondition();
    if (isInError && error != null) {
      ClientSupport.appendError(error.getServiceNotAvailableError(), b);
      ClientSupport.appendError(error.getOtherError(), b);
      if (error.getDescription() != null
          && error.getDescription().getValue() != null)
        b.append(" errorDescription=" + error.getDescription().getValue());
    }

    _log.warn(b.toString());
  }

  /****
   * Internal Classes
   ****/

  private class CheckStatusTask implements Runnable {

    private final ClientSubscriptionChannel _channel;

    public CheckStatusTask(ClientSubscriptionChannel channel) {
      _channel = channel;
    }

    @Override
    public void run() {
      checkStatus(_channel);
    }
  }

  private class PendingCheckStatusTimeoutTask implements Runnable {

    private final ClientSubscriptionChannel _channel;

    private final String _messageId;

    public PendingCheckStatusTimeoutTask(ClientSubscriptionChannel channel,
        String messageId) {
      _channel = channel;
      _messageId = messageId;
    }

    @Override
    public void run() {

      _log.debug("check status timeout task: messageId={}", _messageId);

      PendingCheckStatusRequest pending = _pendingCheckStatusRequests.remove(_messageId);

      /**
       * If the pending check status is null, it (hopefully) means the task
       * completely successfully
       */
      if (pending == null)
        return;

      /**
       * The check status did not succeed, so we attempt to reconnect the
       * channel
       */
      _log.warn("check status failed: address=" + _channel.getAddress());
      _subscriptionManager.handleChannelDisconnectAndReconnect(_channel);
    }
  }

  private static class PendingCheckStatusRequest {

    private final ClientSubscriptionChannel _channel;

    private ScheduledFuture<?> _timeoutTask;

    public PendingCheckStatusRequest(ClientSubscriptionChannel channel,
        ScheduledFuture<?> timeoutTask) {
      _channel = channel;
      _timeoutTask = timeoutTask;
    }

    public ClientSubscriptionChannel getChannel() {
      return _channel;
    }

    public ScheduledFuture<?> getTimeoutTask() {
      return _timeoutTask;
    }
  }

}
