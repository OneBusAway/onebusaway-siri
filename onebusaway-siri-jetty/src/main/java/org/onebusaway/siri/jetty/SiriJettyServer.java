package org.onebusaway.siri.jetty;

import java.net.URL;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiriJettyServer extends SiriServer {

  private static Logger _log = LoggerFactory.getLogger(SiriJettyServer.class);

  private Server _webServer;

  public void start() throws SiriException {

    super.start();

    SubscriptionServerServlet servlet = new SubscriptionServerServlet();
    servlet.setSiriListener(this);

    String serverUrl = _serverUrl;
    if (_privateServerUrl != null)
      serverUrl = _privateServerUrl;

    URL url = url(serverUrl);

    _webServer = new Server(url.getPort());
    Context root = new Context(_webServer, "/", Context.SESSIONS);
    root.addServlet(new ServletHolder(servlet), "/*");

    try {
      _webServer.start();
    } catch (Exception ex) {
      throw new SiriException("error starting SiriServer", ex);
    }

    if (_log.isDebugEnabled())
      _log.debug("SiriServer started at address " + serverUrl);
  }

  public void stop() {

    super.stop();

    if (_webServer != null) {
      try {
        _webServer.stop();
      } catch (Exception ex) {
        _log.warn("error stoping SiriServer", ex);
      }
      _webServer = null;
    }

    if (_log.isDebugEnabled())
      _log.debug("SiriServer stopped");
  }

}
