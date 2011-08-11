package org.onebusaway.siri.core.subscriptions.client;

import com.google.inject.AbstractModule;

public class SiriClientSubscriptionModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SiriClientSubscriptionManager.class);
    bind(CheckStatusManager.class);
    bind(InitiateSubscriptionsManager.class);
    bind(TerminateSubscriptionsManager.class);
  }
}
