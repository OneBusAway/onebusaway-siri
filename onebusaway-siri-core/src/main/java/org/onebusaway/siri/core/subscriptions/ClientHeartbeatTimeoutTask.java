package org.onebusaway.siri.core.subscriptions;

import org.onebusaway.siri.core.SiriClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This task is run when a {@link SiriClient} heartbeat times out, canceling the
 * active subscription for that connection.
 * 
 * @author bdferris
 */
class ClientHeartbeatTimeoutTask implements Runnable {

  private static final Logger _log = LoggerFactory.getLogger(ClientHeartbeatTimeoutTask.class);

  private final SiriClientSubscriptionManager manager;
  
  private final ClientSubscriptionChannel channel;

  public ClientHeartbeatTimeoutTask(SiriClientSubscriptionManager manager,
      ClientSubscriptionChannel channel) {
    this.manager = manager;
    this.channel = channel;
  }

  @Override
  public void run() {
    _log.warn("heartbeat interval timeout: " + channel.getAddress());
    manager.handleDisconnectAndReconnect(channel);
  }
}