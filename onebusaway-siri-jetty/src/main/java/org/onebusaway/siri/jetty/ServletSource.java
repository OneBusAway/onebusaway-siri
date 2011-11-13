/**
 * Copyright (C) 2011 Google, Inc.
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

import javax.servlet.Servlet;

/**
 * Supports generic injection and configuration of a Servlet into the SIRI Jetty
 * HTTP service exporter. By default, the {@link SiriJettyModule} looks for
 * injected instances of {@link SiriCommon} and wraps them in a {@link Servlet}
 * to export them at their desired URL within our embedded Jetty instance.
 * However, there may be other services that wish to be exported in the Jetty
 * instance as well. They can do so by implementing this interface.
 * 
 * @author bdferris
 * 
 */
public interface ServletSource {

  /**
   * The value of {@link URL#getPath()} determines the path where the
   * {@link Servlet} will be exposed. The value of {@link URL#getPort()} will
   * also be used to determine the set of ports the embedded Jetty instance will
   * listen to.
   * 
   * @return the URL where the servlet will be exposed
   */
  public URL getUrl();

  /**
   * @return the actual servlet to expose
   */
  public Servlet getServlet();
}
