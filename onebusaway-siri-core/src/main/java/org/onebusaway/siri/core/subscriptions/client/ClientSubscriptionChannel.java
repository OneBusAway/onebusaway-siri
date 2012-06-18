/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2011 Google, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.core.subscriptions.client;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.subscriptions.SubscriptionSupport;
import org.onebusaway.siri.core.versioning.ESiriVersion;

class ClientSubscriptionChannel {

  private final String address;

  private final ESiriVersion targetVersion;

  private final Set<SubscriptionId> _subscriptions = new HashSet<SubscriptionId>();

  private final Date creationTime = new Date();

  private Date lastServiceStartedTime = null;

  private String manageSubscriptionUrl = null;

  private String checkStatusUrl;

  private long checkStatusInterval = 0;

  private ScheduledFuture<?> checkStatusTask;

  private int heartbeatInterval = 0;

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

  public int getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(int heartbeatInterval) {
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

  public synchronized void getStatus(String prefix, Map<String, String> status) {
    status.put(prefix + ".creationTime",
        SubscriptionSupport.getDateAsString(creationTime));
    if (lastServiceStartedTime != null) {
      status.put(prefix + ".lastServiceStartedTime",
          SubscriptionSupport.getDateAsString(lastServiceStartedTime));
    }
  }

  @Override
  public String toString() {
    return "ClientSubscriptionChannel(address=" + address + ")";
  }
}
