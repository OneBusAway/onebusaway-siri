package org.onebusaway.siri.core.subscriptions;

import org.onebusaway.siri.core.handlers.SiriClientHandler;

abstract class AbstractManager {

  protected SiriClientHandler _client;

  protected SiriClientSubscriptionManager _subscriptionManager;

  protected ClientSupport _support;

  public void setClient(SiriClientHandler client) {
    _client = client;;
  }

  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  public void setSupport(ClientSupport support) {
    _support = support;
  }
}
