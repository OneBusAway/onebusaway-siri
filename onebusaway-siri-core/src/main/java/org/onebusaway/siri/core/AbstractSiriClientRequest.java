package org.onebusaway.siri.core;

import org.onebusaway.siri.core.versioning.ESiriVersion;

public abstract class AbstractSiriClientRequest {

  private String targetUrl;

  private ESiriVersion targetVersion;

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
}
