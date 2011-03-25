package org.onebusaway.siri.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;

import org.onebusaway.siri.core.versioning.ESiriVersion;

public class ServerSubscriptionChannel {

  private final ServerSubscriptionChannelId id;

  private final ESiriVersion targetVersion;

  private final ConcurrentMap<String, ServerSubscriptionInstance> _subscriptions = new ConcurrentHashMap<String, ServerSubscriptionInstance>();

  private long heartbeatInterval = 0;

  private ScheduledFuture<?> heartbeatTask;

  public ServerSubscriptionChannel(ServerSubscriptionChannelId id,
      ESiriVersion targetVersion) {
    this.id = id;
    this.targetVersion = targetVersion;
  }

  public ServerSubscriptionChannelId getId() {
    return id;
  }

  public ESiriVersion getTargetVersion() {
    return targetVersion;
  }

  public String getConsumerAddress() {
    return id.getAddress();
  }

  public ConcurrentMap<String, ServerSubscriptionInstance> getSubscriptions() {
    return _subscriptions;
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
