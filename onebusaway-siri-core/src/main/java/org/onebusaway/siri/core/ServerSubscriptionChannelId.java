package org.onebusaway.siri.core;

public final class ServerSubscriptionChannelId {

  private final String subscriberId;

  private final String address;

  public ServerSubscriptionChannelId(String subscriberId, String address) {
    if (subscriberId == null)
      throw new IllegalArgumentException("subscriberId is null");
    if (address == null)
      throw new IllegalArgumentException("address is null");
    this.subscriberId = subscriberId;
    this.address = address;
  }

  public String getSubscriberId() {
    return subscriberId;
  }

  public String getAddress() {
    return address;
  }

  @Override
  public String toString() {
    return "SubscriptionChannelId(subscriberId=" + subscriberId
        + " address=" + address + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + address.hashCode();
    result = prime * result + subscriberId.hashCode();
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
    ServerSubscriptionChannelId other = (ServerSubscriptionChannelId) obj;
    if (!address.equals(other.address))
      return false;
    if (!subscriberId.equals(other.subscriberId))
      return false;
    return true;
  }

}
