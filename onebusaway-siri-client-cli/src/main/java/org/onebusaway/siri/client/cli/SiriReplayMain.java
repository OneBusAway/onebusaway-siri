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
package org.onebusaway.siri.client.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusaway.siri.core.SiriCoreModule;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.handlers.SiriSubscriptionManagerListener;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionManager;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.onebusaway.siri.jetty.SiriJettyModule;
import org.onebusaway.status_exporter.StatusServletSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.Siri;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class SiriReplayMain {

  private static final Logger _log = LoggerFactory.getLogger(SiriReplayMain.class);

  private static final String ARG_ID = "id";

  private static final String ARG_SERVER_URL = "serverUrl";

  private static final String ARG_PRIVATE_SERVER_URL = "privateServerUrl";

  private static final String ARG_IN_REALTIME = "inRealtime";

  private static final String ARG_NO_WAIT_FOR_SUBSCRIPTION = "noWaitForSubscription";

  private static final String ARG_DEFAULT_DELAY = "defaultDelay";

  private SiriServer _server;

  private SchedulingService _schedulingService;

  private SiriServerSubscriptionManager _subscriptionManager;

  private LifecycleService _lifecycleService;

  private Future<?> _task = null;

  private Deque<File> _dataFiles = new ArrayDeque<File>();

  /**
   * When true, the SIRI messages will be replayed in realtime, with appropriate
   * pauses inserted between deliveries to replicate the timing of the original
   * messages.
   */
  private boolean _inRealtime = false;

  private boolean _noWaitForSubscription = false;
  
  private long _defaultDelay = 1000;

  public static void main(String[] args) {

    try {
      SiriReplayMain m = new SiriReplayMain();
      m.run(args);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
  }

  @Inject
  public void setServer(SiriServer client) {
    _server = client;
  }

  @Inject
  public void setSubscriptionManager(
      SiriServerSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  @Inject
  public void setScheduleService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  @Inject
  public void setLifecycleService(LifecycleService lifecycleService) {
    _lifecycleService = lifecycleService;
  }

  @Inject
  public void setStatusServletSource(StatusServletSource statusServletSource) {
    /**
     * Right now, this is a noop that is here mostly to guarantee that the
     * StatusServletSource is instantiated. However, it could be used in the
     * future to override the default uri for the servlet if desired.
     */
  }

  public void run(String[] args) throws Exception {

    if (SiriLibrary.needsHelp(args)) {
      printUsage();
      System.exit(0);
    }

    Options options = new Options();
    buildOptions(options);

    PosixParser parser = new PosixParser();
    CommandLine cli = parser.parse(options, args);

    args = cli.getArgs();

    if (args.length != 1) {
      printUsage();
      System.exit(-1);
    }

    Set<Module> modules = new HashSet<Module>();
    SiriCoreModule.addModuleAndDependencies(modules);
    SiriJettyModule.addModuleAndDependencies(modules);
    Injector injector = Guice.createInjector(modules);

    injector.injectMembers(this);

    if (cli.hasOption(ARG_ID))
      _server.setIdentity(cli.getOptionValue(ARG_ID));

    if (cli.hasOption(ARG_SERVER_URL))
      _server.setUrl(cli.getOptionValue(ARG_SERVER_URL));

    if (cli.hasOption(ARG_PRIVATE_SERVER_URL))
      _server.setPrivateUrl(cli.getOptionValue(ARG_PRIVATE_SERVER_URL));

    _inRealtime = cli.hasOption(ARG_IN_REALTIME);
    _noWaitForSubscription = cli.hasOption(ARG_NO_WAIT_FOR_SUBSCRIPTION);
    
    if (cli.hasOption(ARG_DEFAULT_DELAY)) {
      _defaultDelay = Long.parseLong(cli.getOptionValue(ARG_DEFAULT_DELAY));
    }

    _subscriptionManager.addListener(new SiriSubscriptionManagerListenerImpl());

    File dataDir = new File(args[0]);
    for (File file : dataDir.listFiles()) {
      if (!file.getName().endsWith(".xml"))
        continue;
      _dataFiles.add(file);
    }

    /**
     * Need to make sure that we fire off all the @PostConstruct service
     * annotations. The @PreDestroy annotations are automatically included in a
     * shutdown hook.
     */
    _lifecycleService.start();

    checkExecutor();
  }

  /****
   * Private Methods
   ****/

  private void printUsage() {

    InputStream is = getClass().getResourceAsStream("usage-replay.txt");
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    String line = null;
    try {
      while ((line = reader.readLine()) != null) {
        System.err.println(line);
      }
    } catch (IOException ex) {

    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ex) {

        }
      }
    }
  }

  private void buildOptions(Options options) {
    options.addOption(ARG_ID, true, "id");
    options.addOption(ARG_SERVER_URL, true, "siri client url");
    options.addOption(ARG_PRIVATE_SERVER_URL, true, "siri private client url");
    options.addOption(ARG_IN_REALTIME, false, "generate records in realtime");
    options.addOption(ARG_NO_WAIT_FOR_SUBSCRIPTION, false,
        "wait for a subscription before sending data");
    options.addOption(ARG_DEFAULT_DELAY,true,"delay, in ms");
  }

  private synchronized void checkExecutor() {

    List<String> channels = _subscriptionManager.getActiveSubscriptionChannels();
    int numberOfActiveChannels = channels.size();

    if (_task == null && (_noWaitForSubscription || numberOfActiveChannels > 0)) {
      _log.info("starting task");
      _task = _schedulingService.submit(new DataSourceTask());
    } else if (_task != null
        && (_noWaitForSubscription || numberOfActiveChannels == 0)) {
      _log.info("stopping task");
      _task.cancel(true);
      _task = null;
    }
  }

  private class SiriSubscriptionManagerListenerImpl implements
      SiriSubscriptionManagerListener {

    @Override
    public void subscriptionAdded(SiriServerSubscriptionManager manager) {
      checkExecutor();
    }

    @Override
    public void subscriptionRemoved(SiriServerSubscriptionManager manager) {
      checkExecutor();
    }
  }

  private class DataSourceTask implements Runnable {

    private final SiriVersioning _versioning = SiriVersioning.getInstance();

    @Override
    public void run() {

      if (_dataFiles.isEmpty()) {
        return;
      }

      _log.info("remaining files: " + _dataFiles.size());

      File file = _dataFiles.poll();

      try {

        ServiceDelivery delivery = readServiceDelivery(file);
        if (delivery != null) {
          _server.publish(delivery);
        }

        if (_dataFiles.isEmpty()) {
          return;
        }

        long pause = computePause(delivery);
        _schedulingService.schedule(this, pause, TimeUnit.MILLISECONDS);

      } catch (Exception ex) {
        _log.warn("error processing file: " + file, ex);
      }

    }

    private ServiceDelivery readServiceDelivery(File file) throws IOException {
      Reader reader = new FileReader(file);
      Object object = _server.unmarshall(reader);
      reader.close();

      Siri siri = (Siri) _versioning.getPayloadAsVersion(object,
          ESiriVersion.V1_3);
      return siri.getServiceDelivery();
    }

    private long computePause(ServiceDelivery delivery) throws IOException {

      long delta = _defaultDelay;
      if (!_inRealtime) {
        return delta;
      }
      Date time = delivery.getResponseTimestamp();
      if (time == null) {
        return delta;
      }

      while (!_dataFiles.isEmpty()) {
        File next = _dataFiles.peek();
        ServiceDelivery nextDelivery = readServiceDelivery(next);
        if (nextDelivery == null) {
          _dataFiles.poll();
        } else {
          Date nextTimestamp = nextDelivery.getResponseTimestamp();
          if (nextTimestamp != null) {
            return nextTimestamp.getTime() - time.getTime();
          }
          return 0;
        }
      }

      return 0;
    }
  }
}
