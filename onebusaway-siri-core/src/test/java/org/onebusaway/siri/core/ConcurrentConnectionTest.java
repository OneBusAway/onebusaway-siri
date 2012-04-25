/**
 * Copyright (C) 2012 Google, Inc.
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
package org.onebusaway.siri.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.onebusaway.guice.jetty_exporter.JettyExporterModule;
import org.onebusaway.guice.jetty_exporter.ServletSource;
import org.onebusaway.guice.jsr250.JSR250Module;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.Siri;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class ConcurrentConnectionTest {
  @Test
  public void test() throws MalformedURLException, InterruptedException {

    String port = System.getProperty("org_onebusaway_test_port", "8080");
    URL url = new URL("http://localhost:" + port + "/test.xml");

    Set<Module> serverModules = new HashSet<Module>();
    JettyExporterModule.addModuleAndDependencies(serverModules);
    JSR250Module.addModuleAndDependencies(serverModules);
    Injector serverInjector = Guice.createInjector(serverModules);
    SimpleServlet servlet = serverInjector.getInstance(SimpleServlet.class);
    servlet.setUrl(url);

    serverInjector.getInstance(LifecycleService.class).start();

    /**
     * Now start our client.
     */
    Set<Module> modules = new HashSet<Module>();
    SiriCoreModule.addModuleAndDependencies(modules);
    Injector injector = Guice.createInjector(modules);

    SiriClient client = injector.getInstance(SiriClient.class);

    LifecycleService lifecycleService = injector.getInstance(LifecycleService.class);
    lifecycleService.start();

    int connectionCount = 20;
    for (int i = 0; i < connectionCount; i++) {
      SiriClientRequest request = new SiriClientRequest();
      request.setTargetUrl(url.toExternalForm());
      request.setTargetVersion(ESiriVersion.V1_3);
      request.setPayload(new Siri());

      client.handleRequest(request);
    }

    Thread.sleep(2000);

    assertEquals(connectionCount, servlet.getConnectionCount());
  }

  private static class SimpleServlet extends HttpServlet implements
      ServletSource {

    private static final long serialVersionUID = 1L;
    private URL _url;
    private AtomicInteger _connectionCount = new AtomicInteger();

    public void setUrl(URL url) {
      _url = url;
    }

    public int getConnectionCount() {
      return _connectionCount.intValue();
    }

    @Override
    public URL getUrl() {
      return _url;
    }

    @Override
    public Servlet getServlet() {
      return this;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      _connectionCount.incrementAndGet();
    }
  }
}
