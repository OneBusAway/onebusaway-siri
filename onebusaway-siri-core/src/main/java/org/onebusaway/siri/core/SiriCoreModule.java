package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.siri.core.guice.JSR250Module;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.subscriptions.client.SiriClientSubscriptionModule;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

public class SiriCoreModule extends AbstractModule {

  public static List<Module> getModules() {
    List<Module> modules = new ArrayList<Module>();
    modules.add(new SiriCoreModule());
    modules.add(new SiriClientSubscriptionModule());
    modules.add(new JSR250Module());
    return modules;
  }

  @Override
  protected void configure() {
    bind(SiriClient.class);
    bind(SiriServer.class);
    bind(SiriClientHandler.class).to(SiriClient.class);
    bind(SchedulingService.class).to(SchedulingServiceImpl.class);
  }
}
