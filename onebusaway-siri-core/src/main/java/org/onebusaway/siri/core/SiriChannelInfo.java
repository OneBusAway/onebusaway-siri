package org.onebusaway.siri.core;

/**
 * Information associated with a particular SIRI channel. Every SIRI
 * subscription has a data channel associated with it. Multiple subscriptions
 * can refer to the same channel, in fact.
 * 
 * @author bdferris
 * 
 */
public class SiriChannelInfo {

  private Object context;

  /**
   * User-supplied context data associated with this channel. See
   * {@link SiriClientRequest#getChannelContext()}. Think of this a
   * user-supplied callback data associated with your subscription channel.
   * 
   * @param <T>
   * @return any user-supplied context data associated with this channel, or
   *         null if not set
   */
  @SuppressWarnings("unchecked")
  public <T> T getContext() {
    return (T) context;
  }

  public void setContext(Object context) {
    this.context = context;
  }
}
