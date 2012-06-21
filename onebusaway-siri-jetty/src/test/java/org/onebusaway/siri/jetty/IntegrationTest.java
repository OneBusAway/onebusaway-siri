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
package org.onebusaway.siri.jetty;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusaway.siri.core.SiriCommon;
import org.onebusaway.siri.core.SiriCoreModule;
import org.onebusaway.status_exporter.StatusServletSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class IntegrationTest {
  @Test
  public void test() throws IOException {
    Set<Module> modules = new HashSet<Module>();
    SiriJettyModule.addModuleAndDependencies(modules);
    SiriCoreModule.addModuleAndDependencies(modules);
    Injector injector = Guice.createInjector(modules);

    MySiriThing mySiriThing = injector.getInstance(MySiriThing.class);

    String port = System.getProperty("org_onebusaway_test_port", "8080");
    URL url = new URL("http://localhost:" + port + "/my-servlet");
    mySiriThing.setUrl(url.toExternalForm());

    injector.getInstance(StatusServletSource.class);

    LifecycleService lifecycleService = injector.getInstance(LifecycleService.class);
    lifecycleService.start();

    BufferedReader reader = new BufferedReader(new InputStreamReader(
        url.openStream()));
    String line = reader.readLine();
    assertEquals("Hello world!", line);
    reader.close();

    URL statusUrl = new URL("http://localhost:" + port + "/status");
    reader = new BufferedReader(new InputStreamReader(statusUrl.openStream()));
    Map<String, String> status = new HashMap<String, String>();
    while ((line = reader.readLine()) != null) {
      int index = line.indexOf("=");
      String key = line.substring(0, index);
      String value = line.substring(index + 1);
      status.put(key, value);
    }
    assertEquals(2, status.size());
    assertEquals("1", status.get("a"));
    assertEquals("2", status.get("b"));

    lifecycleService.stop();
  }

  public static class MySiriThing extends SiriCommon {

    @Override
    public void handleRawRequest(Reader reader, Writer writer) {
      try {
        writer.write("Hello world!");
      } catch (IOException ex) {
        throw new IllegalStateException(ex);
      }
    }

    @Override
    public void getStatus(Map<String, String> status) {
      status.put("a", "1");
      status.put("b", "2");
    }
  }
}
