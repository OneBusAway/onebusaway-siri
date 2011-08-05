package org.onebusaway.siri.core.subscriptions.client;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;

class ClientSubscriptionChannel {

  private final String address;

  private final ESiriVersion targetVersion;

  private final Set<SubscriptionId> _subscriptions = new HashSet<SubscriptionId>();

  private Date lastServiceStartedTime = null;

  private String manageSubscriptionUrl = null;

  private String checkStatusUrl;

  private long checkStatusInterval = 0;

  private ScheduledFuture<?> checkStatusTask;

  private long heartbeatInterval = 0;

  private ScheduledFuture<?> heartbeatTask;

  private int reconnectionAttempts = 0;

  private int reconnectionInterval = 0;

  private Object context;

  public ClientSubscriptionChannel(String address, ESiriVersion targetVersion) {
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
    return _subscriptions;
  }

  public Date getLastServiceStartedTime() {
    return lastServiceStartedTime;
  }

  public void setLastServiceStartedTime(Date lastServiceStartedTime) {
    this.lastServiceStartedTime = lastServiceStartedTime;
  }

  public String getManageSubscriptionUrl() {
    return manageSubscriptionUrl;
  }

  public void setManageSubscriptionUrl(String manageSubscriptionUrl) {
    this.manageSubscriptionUrl = manageSubscriptionUrl;
  }

  public String getCheckStatusUrl() {
    return checkStatusUrl;
  }

  public void setCheckStatusUrl(String checkStatusUrl) {
    this.checkStatusUrl = checkStatusUrl;
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

  public Object getContext() {
    return context;
  }

  public void setContext(Object context) {
    this.context = context;
  }

  @Override
  public String toString() {
    return "ClientSubscriptionChannel(address=" + address + ")";
  }
}
