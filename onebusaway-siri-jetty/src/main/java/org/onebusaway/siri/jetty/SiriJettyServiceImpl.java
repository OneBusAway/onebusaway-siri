package org.onebusaway.siri.jetty;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.onebusaway.collections.FactoryMap;
import org.onebusaway.siri.core.SiriCommon;
import org.onebusaway.siri.core.exceptions.SiriException;

public class SiriJettyServiceImpl {

  private final List<Server> _servers = new ArrayList<Server>();

  private final List<SiriCommon> _services;

  public SiriJettyServiceImpl(List<SiriCommon> services) {
    _services = services;
  }

  @PostConstruct
  public void start() throws SiriException {

    Map<Integer, List<SiriCommon>> servicesByPort = groupServicesByPort(_services);

    for (int port : servicesByPort.keySet()) {

      Server server = new Server(port);
      Context context = new Context(server, "/", Context.SESSIONS);

      for (SiriCommon service : servicesByPort.get(port)) {

        SubscriptionServerServlet servlet = new SubscriptionServerServlet();
        servlet.setSiriListener(service);

        URL url = service.getInternalUrlToBind(false);
        context.addServlet(new ServletHolder(servlet), url.getPath());
      }

      _servers.add(server);
    }

    try {
      for (Server server : _servers) {
        server.start();
      }
    } catch (Exception ex) {
      throw new SiriException("error starting Jetty webserver", ex);
    }
  }

  @PreDestroy
  public void stop() {

    try {
      for (Server server : _servers) {
        server.stop();
      }
    } catch (Exception ex) {
      throw new SiriException("error stopping Jetty webserver", ex);
    }
  }

  /****
   * Private Methods
   ****/

  private Map<Integer, List<SiriCommon>> groupServicesByPort(
      List<SiriCommon> services) {

    Map<Integer, List<SiriCommon>> servicesByPort = new FactoryMap<Integer, List<SiriCommon>>(
        new ArrayList<SiriCommon>());

    for (SiriCommon service : services) {
      URL url = service.getInternalUrlToBind(false);
      int port = url.getPort();
      servicesByPort.get(port).add(service);
    }

    return servicesByPort;
  }

}
