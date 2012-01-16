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
package org.onebusaway.siri.repeater;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.onebusaway.guice.jsr250.LifecycleService;

import com.google.inject.Injector;

public class SiriRepeaterDaemon implements Daemon {

  private LifecycleService _lifecycleService;

  @Override
  public void init(DaemonContext context) throws DaemonInitException, Exception {

    String[] args = context.getArguments();

    SiriRepeaterCommandLineConfiguration config = new SiriRepeaterCommandLineConfiguration();
    Injector injector = config.configure(args);
    _lifecycleService = injector.getInstance(LifecycleService.class);
  }

  @Override
  public void start() throws Exception {
    _lifecycleService.start();
  }

  @Override
  public void stop() throws Exception {
    _lifecycleService.stop();
  }

  @Override
  public void destroy() {
    _lifecycleService = null;
  }
}
