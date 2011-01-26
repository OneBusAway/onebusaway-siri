package org.onebusaway.siri.jetty;

import java.net.URL;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiriJettyClient extends SiriClient {

  private static Logger _log = LoggerFactory.getLogger(SiriJettyClient.class);

  private Server _webServer;

  public void start() {
    
    super.start();

    SubscriptionServerServlet servlet = new SubscriptionServerServlet();
    servlet.setSiriListener(this);

    String clientUrl = _clientUrl;
    if (_privateClientUrl != null)
      clientUrl = _privateClientUrl;

    URL url = url(clientUrl);

    _webServer = new Server(url.getPort());

    Context root = new Context(_webServer, "/", Context.SESSIONS);
    root.addServlet(new ServletHolder(servlet), "/*");

    try {
      _webServer.start();
    } catch (Exception ex) {
      throw new SiriException("error starting SiriServer", ex);
    }
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
  }
}
