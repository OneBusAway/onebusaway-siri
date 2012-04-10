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
package org.onebusaway.siri.client.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriClientRequestFactory;
import org.onebusaway.siri.core.SiriCommon.ELogRawXmlType;
import org.onebusaway.siri.core.SiriCoreModule;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.exceptions.SiriUnknownVersionException;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.siri.jetty.SiriJettyModule;
import org.onebusaway.siri.jetty.StatusServletSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.Siri;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class SiriClientMain {

  private static final Logger _log = LoggerFactory.getLogger(SiriClientMain.class);

  private static final String ARG_ID = "id";

  private static final String ARG_CLIENT_URL = "clientUrl";

  private static final String ARG_PRIVATE_CLIENT_URL = "privateClientUrl";

  private static final String ARG_OUTPUT = "output";

  private static final String ARG_RESPONSE_TIMEOUT = "responseTimeout";

  private static final String ARG_NO_SUBSCRIPTIONS = "noSubscriptions";

  private static final String ARG_LOG_RAW_XML = "logRawXml";

  private static final String ARG_FORMAT_OUTPUT_XML = "formatOutputXml";

  private static final String ARG_SUBSCRIBE = "subscribe";

  private static final String ARG_CHECK_STATUS = "checkStatus";

  private static final String ARG_TERMINATE_SUBSCRIPTION = "terminateSubscription";

  private SiriClient _client;

  private SchedulingService _schedulingService;

  private LifecycleService _lifecycleService;

  private String _outputFormat;

  private String _outputName;

  private PrintWriter _output;

  public static void main(String[] args) {

    try {
      SiriClientMain m = new SiriClientMain();
      m.run(args);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
  }

  @Inject
  public void setClient(SiriClient client) {
    _client = client;
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

    List<Module> modules = new ArrayList<Module>();
    modules.addAll(SiriCoreModule.getModules());
    modules.add(new SiriJettyModule());
    Injector injector = Guice.createInjector(modules);

    injector.injectMembers(this);

    if (cli.hasOption(ARG_ID))
      _client.setIdentity(cli.getOptionValue(ARG_ID));

    if (cli.hasOption(ARG_CLIENT_URL))
      _client.setUrl(cli.getOptionValue(ARG_CLIENT_URL));

    if (cli.hasOption(ARG_PRIVATE_CLIENT_URL))
      _client.setPrivateUrl(cli.getOptionValue(ARG_PRIVATE_CLIENT_URL));

    if (cli.hasOption(ARG_LOG_RAW_XML)) {
      String value = cli.getOptionValue(ARG_LOG_RAW_XML);
      ELogRawXmlType type = ELogRawXmlType.valueOf(value.toUpperCase());
      _client.setLogRawXmlType(type);
    }

    if (cli.hasOption(ARG_OUTPUT)) {
      _outputFormat = cli.getOptionValue(ARG_OUTPUT);
    } else {
      _output = new PrintWriter(System.out);
    }

    if (cli.hasOption(ARG_RESPONSE_TIMEOUT)) {
      int responseTimeout = Integer.parseInt(cli.getOptionValue(ARG_RESPONSE_TIMEOUT));
      _schedulingService.setResponseTimeout(responseTimeout);
    }

    _client.setFormatOutputXmlByDefault(cli.hasOption(ARG_FORMAT_OUTPUT_XML));

    _client.addServiceDeliveryHandler(new ServiceDeliveryHandlerImpl());

    if (args.length == 0 && !cli.hasOption(ARG_NO_SUBSCRIPTIONS)) {
      printUsage();
      System.exit(-1);
    }

    /**
     * Need to make sure that we fire off all the @PostConstruct service
     * annotations. The @PreDestroy annotations are automatically included in a
     * shutdown hook.
     */
    _lifecycleService.start();

    ERequestType requestType = getRequestType(cli);
    SiriClientRequestFactory factory = new SiriClientRequestFactory();

    for (String arg : args) {
      SiriClientRequest request = getLineAsRequest(requestType, factory, arg);
      Siri delivery = _client.handleRequestWithResponse(request);
      if (delivery != null)
        printAsXml(delivery);
    }
  }

  /****
   * Private Methods
   ****/

  private ERequestType getRequestType(CommandLine cli) {
    if (cli.hasOption(ARG_CHECK_STATUS))
      return ERequestType.CHECK_STATUS;
    if (cli.hasOption(ARG_SUBSCRIBE))
      return ERequestType.SUBSCRIPTION;
    if (cli.hasOption(ARG_TERMINATE_SUBSCRIPTION))
      return ERequestType.TERMINATE_SUBSCRIPTION;
    return ERequestType.SERVICE_REQUEST;
  }

  private SiriClientRequest getLineAsRequest(ERequestType requestType,
      SiriClientRequestFactory factory, String arg) {

    try {
      Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
      switch (requestType) {
        case CHECK_STATUS:
          return factory.createCheckStatusRequest(subArgs);
        case SERVICE_REQUEST:
        case SUBSCRIPTION:
          /**
           * If the user hasn't specifically specified a "Subscribe" arg in
           * their client request args, we use the general service request vs
           * subscription direction to indicate what should be done.
           */
          if (!subArgs.containsKey(SiriClientRequestFactory.ARG_SUBSCRIBE)) {
            subArgs.put(SiriClientRequestFactory.ARG_SUBSCRIBE,
                Boolean.toString(requestType == ERequestType.SUBSCRIPTION));
          }
          return factory.createRequest(subArgs);
        case TERMINATE_SUBSCRIPTION:
          return factory.createTerminateSubscriptionRequest(subArgs);
        default:
          throw new SiriException("unknown request type=" + requestType);
      }

    } catch (SiriUnknownVersionException ex) {
      handleUnknownSiriVersion(arg, ex);
    }

    return null;
  }

  private void handleUnknownSiriVersion(String arg,
      SiriUnknownVersionException ex) {
    System.err.println("uknown siri version=\"" + ex.getVersion()
        + "\" in spec=" + arg);
    System.err.println("supported versions:");
    for (ESiriVersion version : ESiriVersion.values())
      System.err.println("  " + version.getVersionId());
    System.exit(-1);
  }

  private synchronized void printAsXml(Object object) throws IOException {

    StringWriter out = new StringWriter();
    _client.marshall(object, out);

    if (_outputFormat != null) {
      String outputName = String.format(_outputFormat, new Date());
      if (_outputName == null || !_outputName.equals(outputName)) {
        _outputName = outputName;
        if (_output != null) {
          _output.close();
        }
        File outputFile = new File(_outputName);
        File parentDirectory = outputFile.getParentFile();
        if( parentDirectory != null && !parentDirectory.exists()) {
          parentDirectory.mkdirs();
        }
        _output = new PrintWriter(new FileWriter(_outputName));
      }
    }

    _output.println(out.toString());
    _output.flush();
  }

  private void printUsage() {

    InputStream is = getClass().getResourceAsStream("usage.txt");
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
    options.addOption(ARG_CLIENT_URL, true, "siri client url");
    options.addOption(ARG_PRIVATE_CLIENT_URL, true, "siri private client url");
    options.addOption(ARG_OUTPUT, true, "output");
    options.addOption(ARG_RESPONSE_TIMEOUT, true, "response timeout");
    options.addOption(ARG_LOG_RAW_XML, true, "log raw xml");
    options.addOption(ARG_FORMAT_OUTPUT_XML, false, "format output xml");
    options.addOption(ARG_NO_SUBSCRIPTIONS, false, "");

    options.addOption(ARG_SUBSCRIBE, false, "subscribe (vs one-time request)");
    options.addOption(ARG_TERMINATE_SUBSCRIPTION, false,
        "terminate the specified subscriptions");
    options.addOption(ARG_CHECK_STATUS, false, "check status");
  }

  private class ServiceDeliveryHandlerImpl implements
      SiriServiceDeliveryHandler {

    @Override
    public void handleServiceDelivery(SiriChannelInfo channelInfo,
        ServiceDelivery serviceDelivery) {
      Siri siri = new Siri();
      siri.setServiceDelivery(serviceDelivery);
      try {
        printAsXml(siri);
      } catch (IOException ex) {
        _log.warn("error writing output", ex);
      }
    }
  }

  private enum ERequestType {
    SERVICE_REQUEST, SUBSCRIPTION, CHECK_STATUS, TERMINATE_SUBSCRIPTION
  }
}
