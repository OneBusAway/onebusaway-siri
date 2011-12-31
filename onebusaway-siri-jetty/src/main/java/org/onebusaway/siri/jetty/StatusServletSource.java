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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.Servlet;

import org.onebusaway.siri.core.services.StatusService;

/**
 * A {@link ServletSource} implementation that can be used to configure the URI
 * and servlet used by the status serving mechanism. See {@link StatusServlet}
 * and {@link StatusService} for more info.
 * 
 * @author bdferris
 * 
 */
@Singleton
public class StatusServletSource implements ServletSource {

  public static final String URL_NAME = "org.onebusaway.siri.jetty.StatusServletSource.url";

  public static final String SERVLET_NAME = "org.onebusaway.siri.jetty.StatusServletSource.servlet";

  private URL _url;

  private Servlet _servlet;

  @Inject
  public void setUrl(@Named(URL_NAME) URL url) {
    _url = url;
  }

  @Inject
  public void setServlet(@Named(SERVLET_NAME) Servlet servlet) {
    _servlet = servlet;
  }

  @Override
  public URL getUrl() {
    return _url;
  }

  @Override
  public Servlet getServlet() {
    return _servlet;
  }
}
