package org.onebusaway.siri.core.subscriptions.server;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;

class ServerSubscriptionChannel {

  /**
   * This is the address where service deliveries will be sent. It uniquely
   * identifies the channel.
   */
  private final String address;

  private final ESiriVersion targetVersion;

  private final Set<SubscriptionId> subscriptions = new HashSet<SubscriptionId>();

  private long heartbeatInterval = 0;

  private ScheduledFuture<?> heartbeatTask;

  public ServerSubscriptionChannel(String address, ESiriVersion targetVersion) {
    this.address = address;
    this.targetVersion = targetVersion;
  }

  public String getAddress() {
    return address;
  }

  public ESiriVersion getTargetVersion() {
    return targetVersion;
  }

  public Set<SubscriptionId> getSubscriptions() {
    return subscriptions;
  }

  public long getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(long heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public ScheduledFuture<?> getHeartbeatTask() {
    return heartbeatTask;
  }

  public void setHeartbeatTask(ScheduledFuture<?> heartbeatTask) {
    this.heartbeatTask = heartbeatTask;
  }
}
