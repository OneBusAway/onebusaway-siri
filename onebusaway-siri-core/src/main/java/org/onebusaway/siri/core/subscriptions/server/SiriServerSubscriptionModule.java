package org.onebusaway.siri.core.subscriptions.server;

import com.google.inject.AbstractModule;

public class SiriServerSubscriptionModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SiriServerSubscriptionManager.class);
  }
}
