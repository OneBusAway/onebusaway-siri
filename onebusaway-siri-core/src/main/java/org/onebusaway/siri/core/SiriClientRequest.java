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

import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.Siri;

public class SiriClientRequest {

  private String targetUrl;

  private String manageSubscriptionUrl;

  private String checkStatusUrl;

  private ESiriVersion targetVersion;

  /**
   * The number of times to attempt to reconnect when a (network) connection
   * fails. Zero indicates that no reconnection attempts will be made while -1
   * indicates that an indefinite number of reconnection attempts will be made.
   */
  private int reconnectionAttempts = 0;

  /**
   * How long to wait between reconnection attempts (seconds)
   */
  private int reconnectionInterval = 60;

  /**
   * The number of remaining reconnection attempts, used to incrementally track
   * how many reconnection attempts have already been made.
   */
  private int remainingReconnectionAttempts = 0;

  /**
   * The number of connection errors for the specified client request
   */
  private int connectionErrorCount = 0;

  /**
   * Interval, in seconds, on which check status requests are sent to the server
   */
  private int checkStatusInterval = 0;

  private int heartbeatInterval = 0;

  private long initialTerminationDuration;

  private Object channelContext;

  private Siri payload;

  public SiriClientRequest() {

  }

  public SiriClientRequest(SiriClientRequest request) {
    this.targetUrl = request.targetUrl;
    this.manageSubscriptionUrl = request.manageSubscriptionUrl;
    this.checkStatusUrl = request.checkStatusUrl;
    this.targetVersion = request.targetVersion;
    this.reconnectionAttempts = request.reconnectionAttempts;
    this.reconnectionInterval = request.reconnectionInterval;
    this.checkStatusInterval = request.checkStatusInterval;
    this.heartbeatInterval = request.heartbeatInterval;
    this.initialTerminationDuration = request.initialTerminationDuration;
  }

  public String getTargetUrl() {
    return targetUrl;
  }

  public void setTargetUrl(String targetUrl) {
    this.targetUrl = targetUrl;
  }

  /**
   * If set, this url should be used for subscription management requests (ex
   * TerminateSubscriptionRequest), in lieu of the normal
   * {@link #getTargetUrl()}.
   * 
   * @return
   */
  public String getManageSubscriptionUrl() {
    return manageSubscriptionUrl;
  }

  public void setManageSubscriptionUrl(String manageSubscriptionUrl) {
    this.manageSubscriptionUrl = manageSubscriptionUrl;
  }

  /**
   * If set, this url should be used for check status requets, in lieu of the
   * normal {@link #getTargetUrl()}.
   * 
   * @return
   */
  public String getCheckStatusUrl() {
    return checkStatusUrl;
  }

  public void setCheckStatusUrl(String checkStatusUrl) {
    this.checkStatusUrl = checkStatusUrl;
  }

  public ESiriVersion getTargetVersion() {
    return targetVersion;
  }

  public void setTargetVersion(ESiriVersion targetVersion) {
    this.targetVersion = targetVersion;
  }

  public int getReconnectionAttempts() {
    return reconnectionAttempts;
  }

  public void setReconnectionAttempts(int reconnectionAttempts) {
    this.reconnectionAttempts = reconnectionAttempts;
  }

  /**
   * 
   * @return time, in seconds, to wait between reconnection attempts
   */
  public int getReconnectionInterval() {
    return reconnectionInterval;
  }

  /**
   * 
   * @param reconnectionInterval time in seconds
   */
  public void setReconnectionInterval(int reconnectionInterval) {
    this.reconnectionInterval = reconnectionInterval;
  }

  public int getRemainingReconnectionAttempts() {
    return remainingReconnectionAttempts;
  }

  public void decrementRemainingReconnctionAttempts() {
    if (this.remainingReconnectionAttempts > 0)
      this.remainingReconnectionAttempts--;
  }

  public int getConnectionErrorCount() {
    return connectionErrorCount;
  }

  public void incrementConnectionErrorCount() {
    this.connectionErrorCount++;
  }

  public void resetConnectionErrorCount() {
    this.connectionErrorCount = 0;
  }

  /**
   * Resets the values of {@link #getConnectionErrorCount()} and
   * {@link #getRemainingReconnectionAttempts()} to their default initial
   * values.
   */
  public void resetConnectionStatistics() {
    this.connectionErrorCount = 0;
    this.remainingReconnectionAttempts = this.reconnectionAttempts;
  }

  public int getCheckStatusInterval() {
    return checkStatusInterval;
  }

  public void setCheckStatusInterval(int checkStatusInterval) {
    this.checkStatusInterval = checkStatusInterval;
  }

  public int getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(int heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public long getInitialTerminationDuration() {
    return initialTerminationDuration;
  }

  public void setInitialTerminationDuration(long initialTerminationDuration) {
    this.initialTerminationDuration = initialTerminationDuration;
  }

  /**
   * See {@link #setChannelContext(Object)}.
   * 
   * @return channel-specific callback data to be associated with the
   *         subscription.
   */
  public Object getChannelContext() {
    return channelContext;
  }

  /**
   * If you wish to associate some channel-specific data with this subscription,
   * you can supply it with this method. Think of this as supplying callback
   * data. The context data will be included in
   * {@link SiriChannelInfo#getContext()} when the siri client notifies you of a
   * new service delivery in a {@link SiriServiceDeliveryHandler}.
   * 
   * @param channelContext
   */
  public void setChannelContext(Object channelContext) {
    this.channelContext = channelContext;
  }

  public Siri getPayload() {
    return payload;
  }

  public void setPayload(Siri payload) {
    this.payload = payload;
  }

}
