/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.onebusaway.guice.jsr250.JSR250Module;
import org.onebusaway.siri.core.services.SiriServicesModule;
import org.onebusaway.siri.core.subscriptions.client.SiriClientSubscriptionModule;
import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionModule;
import org.onebusaway.status_exporter.StatusServiceModule;

import com.google.inject.Module;

public class SiriCoreModule {

  public static void addModuleAndDependencies(Set<Module> modules) {
    for (Module module : getModules()) {
      modules.add(module);
    }
    StatusServiceModule.addModuleAndDependencies(modules);
  }

  public static List<Module> getModules() {
    List<Module> modules = new ArrayList<Module>();
    modules.add(new SiriClientModule());
    modules.add(new SiriServerModule());
    modules.add(new SiriServicesModule());
    modules.add(new SiriClientSubscriptionModule());
    modules.add(new SiriServerSubscriptionModule());
    modules.add(new JSR250Module());
    return modules;
  }
}
