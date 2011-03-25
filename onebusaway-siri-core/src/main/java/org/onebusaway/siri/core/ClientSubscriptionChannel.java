package org.onebusaway.siri.core;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;

import org.onebusaway.siri.core.versioning.ESiriVersion;

public class ClientSubscriptionChannel {

  private final String serverId;

  private final String address;

  private final ESiriVersion targetVersion;

  private final ConcurrentMap<String, ClientSubscriptionInstance> _subscriptions = new ConcurrentHashMap<String, ClientSubscriptionInstance>();

  private Date lastServiceStartedTime = null;

  private long checkStatusInterval = 0;

  private ScheduledFuture<?> checkStatusTask;

  private long heartbeatInterval = 0;

  private ScheduledFuture<?> heartbeatTask;

  private int reconnectionAttempts = 0;

  private int reconnectionInterval = 0;

  public ClientSubscriptionChannel(String serverId, String address,
      ESiriVersion targetVersion) {
    this.serverId = serverId;
    this.address = address;
    this.targetVersion = targetVersion;
  }

  public String getServerId() {
    return serverId;
  }

  public String getAddress() {
    return address;
  }

  public ESiriVersion getTargetVersion() {
    return targetVersion;
  }

  public ConcurrentMap<String, ClientSubscriptionInstance> getSubscriptions() {
    return _subscriptions;
  }

  public Date getLastServiceStartedTime() {
    return lastServiceStartedTime;
  }

  public void setLastServiceStartedTime(Date lastServiceStartedTime) {
    this.lastServiceStartedTime = lastServiceStartedTime;
  }

  public long getCheckStatusInterval() {
    return checkStatusInterval;
  }

  public void setCheckStatusInterval(long checkStatusInterval) {
    this.checkStatusInterval = checkStatusInterval;
  }

  public ScheduledFuture<?> getCheckStatusTask() {
    return checkStatusTask;
  }

  public void setCheckStatusTask(ScheduledFuture<?> checkStatusTask) {
    this.checkStatusTask = checkStatusTask;
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

  public int getReconnectionAttempts() {
    return reconnectionAttempts;
  }

  public void setReconnectionAttempts(int reconnectionAttempts) {
    this.reconnectionAttempts = reconnectionAttempts;
  }

  public int getReconnectionInterval() {
    return reconnectionInterval;
  }

  public void setReconnectionInterval(int reconnectionInterval) {
    this.reconnectionInterval = reconnectionInterval;
  }

  @Override
  public String toString() {
    return "ClientSubscriptionChannel(serverId=" + serverId + " address="
        + address + ")";
  }
}
