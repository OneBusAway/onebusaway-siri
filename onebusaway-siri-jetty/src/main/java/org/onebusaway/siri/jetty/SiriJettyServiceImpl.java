/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.jetty;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.Servlet;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.onebusaway.collections.FactoryMap;
import org.onebusaway.siri.core.exceptions.SiriException;

public class SiriJettyServiceImpl {

  private final List<Server> _servers = new ArrayList<Server>();

  private final List<ServletSource> _services;

  public SiriJettyServiceImpl(List<ServletSource> services) {
    _services = services;
  }

  @PostConstruct
  public void start() throws SiriException {

    Map<Integer, List<ServletSource>> servicesByPort = groupServicesByPort(_services);

    for (int port : servicesByPort.keySet()) {

      Server server = new Server(port);
      Context context = new Context(server, "/", Context.SESSIONS);

      for (ServletSource service : servicesByPort.get(port)) {
        URL url = service.getUrl();
        Servlet servlet = service.getServlet();
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

  private Map<Integer, List<ServletSource>> groupServicesByPort(
      List<ServletSource> services) {

    Map<Integer, List<ServletSource>> servicesByPort = new FactoryMap<Integer, List<ServletSource>>(
        new ArrayList<ServletSource>());

    for (ServletSource service : services) {
      URL url = service.getUrl();
      int port = url.getPort();
      servicesByPort.get(port).add(service);
    }

    /**
     * Services with a URL port of -1 indicate that no port has been specified.
     * If URLs of this form are present, determining the port to use for these
     * services depends on what other services have been specified.
     */
    List<ServletSource> servicesWithDefaultPort = servicesByPort.remove(-1);
    if (servicesWithDefaultPort != null) {
      if (servicesByPort.isEmpty()) {
        /**
         * If no other services have been specified, we just use the default
         * port 80 for the services with a default port value.
         */
        servicesByPort.put(80, servicesWithDefaultPort);
      } else {
        /**
         * If other services HAVE been specified, we bind the default port
         * services to EACH of the ports mentioned by other services. A little
         * bit counter-intuitive, I agree, but it allows the user to change a
         * primary client or server port and have secondary services
         * automatically use that new port.
         */
        for (List<ServletSource> sources : servicesByPort.values()) {
          sources.addAll(servicesWithDefaultPort);
        }
      }
    }

    return servicesByPort;
  }

}
