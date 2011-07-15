package org.onebusaway.siri.core;

import java.util.Date;

import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.Siri;

public class SiriClientRequest {

  private String targetUrl;

  private ESiriVersion targetVersion;

  private int reconnectionAttempts = 0;

  private int reconnectionInterval = 60;

  /**
   * Interval, in seconds, on which check status requests are sent to the server
   */
  private int checkStatusInterval = 0;

  private int heartbeatInterval = 0;

  private Date initialTerminationTime;

  private Object channelContext;

  private Siri payload;

  public SiriClientRequest() {

  }

  public SiriClientRequest(SiriClientRequest request) {
    this.targetUrl = request.targetUrl;
    this.targetVersion = request.targetVersion;
    this.reconnectionAttempts = request.reconnectionAttempts;
    this.reconnectionInterval = request.reconnectionInterval;
    this.checkStatusInterval = request.checkStatusInterval;
    this.heartbeatInterval = request.heartbeatInterval;
    this.initialTerminationTime = request.initialTerminationTime;
  }

  public String getTargetUrl() {
    return targetUrl;
  }

  public void setTargetUrl(String targetUrl) {
    this.targetUrl = targetUrl;
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

  public Date getInitialTerminationTime() {
    return initialTerminationTime;
  }

  public void setInitialTerminationTime(Date initialTerminationTime) {
    this.initialTerminationTime = initialTerminationTime;
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
