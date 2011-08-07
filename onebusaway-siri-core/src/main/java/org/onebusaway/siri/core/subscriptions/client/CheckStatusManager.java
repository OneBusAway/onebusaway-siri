package org.onebusaway.siri.core.subscriptions.client;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.exceptions.SiriMissingArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.MessageRefStructure;
import uk.org.siri.siri.Siri;

class CheckStatusManager extends AbstractManager {

  private static final Logger _log = LoggerFactory.getLogger(CheckStatusManager.class);

  private ConcurrentMap<String, PendingCheckStatusRequest> _pendingCheckStatusRequests = new ConcurrentHashMap<String, PendingCheckStatusRequest>();

  /****
   * 
   ****/

  public void resetCheckStatusTask(ClientSubscriptionChannel channel,
      long checkStatusInterval) {

    _log.debug("resetting check status task: channel={} interval={}",
        channel.getAddress(), checkStatusInterval);

    ScheduledFuture<?> checkStatusTask = channel.getCheckStatusTask();
    if (checkStatusTask != null) {
      checkStatusTask.cancel(true);
      channel.setCheckStatusTask(null);
    }

    if (checkStatusInterval > 0) {
      CheckStatusTask task = new CheckStatusTask(channel);

      checkStatusTask = _subscriptionManager.scheduleAtFixedRate(task,
          checkStatusInterval, checkStatusInterval, TimeUnit.SECONDS);
      channel.setCheckStatusTask(checkStatusTask);
    }
  }

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

    _support.logErrorInCheckStatusResponse(channel, response, isNewer,
        isInError);

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
    if( url == null)
      url = channel.getAddress();

    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl(url);
    request.setTargetVersion(channel.getTargetVersion());
    request.setPayload(siri);

    PendingCheckStatusTimeoutTask timeoutTask = new PendingCheckStatusTimeoutTask(
        channel, messageId.getValue());
    ScheduledFuture<?> future = _subscriptionManager.scheduleResponseTimeoutTask(timeoutTask);

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
