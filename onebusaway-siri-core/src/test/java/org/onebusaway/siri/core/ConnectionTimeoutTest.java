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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.Siri;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class ConnectionTimeoutTest {

  private static final Logger _log = LoggerFactory.getLogger(ConnectionTimeoutTest.class);

  @Test
  public void test() throws IOException {

    /**
     * First start a simple server.
     */
    ServerSocket socket = new ServerSocket();
    socket.bind(null);

    SimpleServer server = new SimpleServer(socket);
    Thread serverThread = new Thread(server);
    serverThread.start();

    /**
     * Now start our client.
     */
    Set<Module> modules = new HashSet<Module>();
    SiriCoreModule.addModuleAndDependencies(modules);
    Injector injector = Guice.createInjector(modules);

    SiriClient client = injector.getInstance(SiriClient.class);
    client.setConnectionTimeout(5);

    LifecycleService lifecycleService = injector.getInstance(LifecycleService.class);
    lifecycleService.start();

    String url = "http://localhost:" + socket.getLocalPort() + "/test.xml";
    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl(url);
    request.setTargetVersion(ESiriVersion.V1_3);
    request.setPayload(new Siri());

    long tStart = System.currentTimeMillis();
    SiriConnectionException ex = null;

    try {
      client.handleRequestWithResponse(request);
      fail();
    } catch (SiriConnectionException ex2) {
      ex = ex2;
    }

    long tEnd = System.currentTimeMillis();

    assertEquals(SocketTimeoutException.class, ex.getCause().getClass());
    assertTrue(tEnd - tStart < 10000);
  }

  private static class SimpleServer implements Runnable {

    private final ServerSocket _socket;

    public SimpleServer(ServerSocket socket) {
      _socket = socket;
    }

    @Override
    public void run() {
      try {
        Socket socket = _socket.accept();
        _log.info("We have a connection!  Now we wait...");
        Thread.sleep(20000);
        socket.close();
      } catch (InterruptedException ex) {
        return;
      } catch (Exception ex) {
        _log.error("error for simple server", ex);
      }
    }
  }
}
