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

  private Context _rootContext;

  public void setWebServer(Server webServer) {
    _webServer = webServer;
  }

  public void setRootContext(Context rootContext) {
    _rootContext = rootContext;
  }

  /****
   * {@link SiriServer} Interface
   ****/

  @Override
  public void start() throws SiriException {

    super.start();

    SubscriptionServerServlet servlet = new SubscriptionServerServlet();
    servlet.setSiriListener(this);

    URL url = getInternalUrlToBind();

    if (_webServer == null)
      _webServer = new Server(url.getPort());

    if (_rootContext == null)
      _rootContext = new Context(_webServer, "/", Context.SESSIONS);

    _rootContext.addServlet(new ServletHolder(servlet), url.getPath());

    try {
      _webServer.start();
    } catch (Exception ex) {
      throw new SiriException("error starting SiriServer", ex);
    }

    if (_log.isDebugEnabled())
      _log.debug("SiriServer started on port " + url.getPort());
  }

  @Override
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
