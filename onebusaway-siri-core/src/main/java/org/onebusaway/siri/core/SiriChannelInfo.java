package org.onebusaway.siri.core;

public class SiriChannelInfo {

  private Object context;

  @SuppressWarnings("unchecked")
  public <T> T getContext() {
    return (T) context;
  }

  public void setContext(Object context) {
    this.context = context;
  }
}
