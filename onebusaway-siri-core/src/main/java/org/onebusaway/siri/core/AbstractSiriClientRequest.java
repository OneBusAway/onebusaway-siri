package org.onebusaway.siri.core;

import org.onebusaway.siri.core.versioning.ESiriVersion;

public abstract class AbstractSiriClientRequest {

  private String targetUrl;

  private ESiriVersion targetVersion;

  private int reconnectionAttempts = 0;

  private int reconnectionInterval = 60;

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

}
