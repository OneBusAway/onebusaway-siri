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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.onebusaway.siri.core.services.StatusService;

/**
 * A servlet that queries the {@link StatusService} and writes the resulting
 * status map out as a "text/plain" response, serializing each map entry as a
 * "key=value" line. The servlet is typically configured by the parent
 * {@link StatusServletSource}.
 * 
 * @author bdferris
 * 
 */
@Singleton
class StatusServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private StatusService _statusService;

  @Inject
  public void setStatusService(StatusService statusService) {
    _statusService = statusService;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    Map<String, String> status = new TreeMap<String, String>();

    _statusService.getStatus(status);

    resp.setContentType("text/plain");

    PrintWriter writer = resp.getWriter();
    for (Map.Entry<String, String> entry : status.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      writer.write(key + "=" + value + "\n");
    }
    writer.close();
  }

}
