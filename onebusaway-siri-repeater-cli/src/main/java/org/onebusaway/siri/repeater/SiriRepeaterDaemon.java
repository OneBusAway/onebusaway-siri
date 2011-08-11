package org.onebusaway.siri.repeater;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.onebusaway.siri.core.guice.LifecycleService;

import com.google.inject.Injector;

public class SiriRepeaterDaemon implements Daemon {

  private LifecycleService _lifecycleService;

  @Override
  public void init(DaemonContext context) throws DaemonInitException, Exception {

    String[] args = context.getArguments();

    SiriRepeaterCommandLineConfiguration config = new SiriRepeaterCommandLineConfiguration();
    Injector injector = config.configure(args);
    _lifecycleService = injector.getInstance(LifecycleService.class);
  }

  @Override
  public void start() throws Exception {
    _lifecycleService.start();
  }

  @Override
  public void stop() throws Exception {
    _lifecycleService.stop();
  }

  @Override
  public void destroy() {
    _lifecycleService = null;
  }
}
