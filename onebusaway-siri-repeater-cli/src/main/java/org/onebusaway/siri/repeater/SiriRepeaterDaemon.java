package org.onebusaway.siri.repeater;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

public class SiriRepeaterDaemon implements Daemon {

  private SiriRepeater _repeater;

  @Override
  public void init(DaemonContext context) throws DaemonInitException, Exception {

    String[] args = context.getArguments();

    SiriRepeaterCommandLineConfiguration config = new SiriRepeaterCommandLineConfiguration();
    _repeater = config.configure(args);
  }

  @Override
  public void start() throws Exception {
    _repeater.start();
  }

  @Override
  public void stop() throws Exception {
    _repeater.stop();
  }

  @Override
  public void destroy() {
    _repeater = null;
  }
}
