package org.onebusaway.siri.core;

public final class ServerSubscriptionInstanceId {

  private final ServerSubscriptionChannelId channelId;

  private final String subscriptionId;

  public ServerSubscriptionInstanceId(ServerSubscriptionChannelId channelId,
      String subscriptionId) {
    if (channelId == null)
      throw new IllegalArgumentException("channelId is null");
    if (subscriptionId == null)
      throw new IllegalArgumentException("subscriptionId is null");
    this.channelId = channelId;
    this.subscriptionId = subscriptionId;
  }

  public ServerSubscriptionChannelId getChannelId() {
    return channelId;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + channelId.hashCode();
    result = prime * result + subscriptionId.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ServerSubscriptionInstanceId other = (ServerSubscriptionInstanceId) obj;
    if (!channelId.equals(other.channelId))
      return false;
    if (!subscriptionId.equals(other.subscriptionId))
      return false;
    return true;
  }
}
