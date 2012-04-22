/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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
package org.onebusaway.siri.repeater;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import org.onebusaway.cli.Daemonizer;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriClientRequestFactory;
import org.onebusaway.siri.core.SiriCommon.ELogRawXmlType;
import org.onebusaway.siri.core.SiriCoreModule;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterFactoryImpl;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterMatcher;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterMatcherFactoryImpl;
import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionManager;
import org.onebusaway.siri.jetty.SiriJettyModule;
import org.onebusaway.siri.jetty.StatusServletSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class SiriRepeaterCommandLineConfiguration {

  private static final String ARG_ID = "id";

  private static final String ARG_REPEATER_URL = "repeaterUrl";

  private static final String ARG_PRIVATE_REPEATER_URL = "privateRepeaterUrl";

  private static final String ARG_CLIENT_URL = "clientUrl";

  private static final String ARG_PRIVATE_CLIENT_URL = "privateClientUrl";

  private static final String ARG_FILTER = "filter";

  private static final String ARG_REQUESTOR_CONSUMER_ADDRESS_DEFAULT = "requestorConsumerAddressDefault";

  private static final String ARG_LOG_RAW_XML = "logRawXml";

  private static final String ARG_FORMAT_OUTPUT_XML = "formatOutputXml";

  private static final String ARG_NO_SUBSCRIPTIONS = "noSubscriptions";

  public Injector configure(String[] args) throws Exception {

    if (needsHelp(args)) {
      printUsage();
      System.exit(0);
    }

    Options options = new Options();
    buildOptions(options);

    Daemonizer.buildOptions(options);

    Parser parser = new PosixParser();
    CommandLine cli = parser.parse(options, args);

    Daemonizer.handleDaemonization(cli);

    args = cli.getArgs();

    if (args.length == 0 && !cli.hasOption(ARG_NO_SUBSCRIPTIONS)) {
      printUsage();
      System.exit(-1);
    }

    Set<Module> modules = new HashSet<Module>();
    SiriCoreModule.addModuleAndDependencies(modules);
    SiriJettyModule.addModuleAndDependencies(modules);
    Injector injector = Guice.createInjector(modules);

    handleCommandLineOptions(cli, injector);

    return injector;
  }

  private boolean needsHelp(String[] args) {
    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help") || arg.equals("-help"))
        return true;
    }
    return false;
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

  protected void buildOptions(Options options) {
    options.addOption(ARG_ID, true, "SIRI client participant id");
    options.addOption(ARG_REPEATER_URL, true, "repeater url");
    options.addOption(ARG_PRIVATE_REPEATER_URL, true, "private repeater url");
    options.addOption(ARG_CLIENT_URL, true, "client url");
    options.addOption(ARG_PRIVATE_CLIENT_URL, true, "private client url");
    options.addOption(ARG_REQUESTOR_CONSUMER_ADDRESS_DEFAULT, true,
        "consumer address default for requestor");
    options.addOption(ARG_LOG_RAW_XML, true, "log raw xml");
    options.addOption(ARG_FORMAT_OUTPUT_XML, false, "format output xml");
    options.addOption(ARG_NO_SUBSCRIPTIONS, false, "no subscriptions");
    options.addOption(ARG_FILTER, true, "filter specification");
  }

  protected void handleCommandLineOptions(CommandLine cli, Injector injector) {

    SiriRepeater siriRepeater = injector.getInstance(SiriRepeater.class);
    SiriClient siriClient = injector.getInstance(SiriClient.class);
    SiriServer siriServer = injector.getInstance(SiriServer.class);
    SiriServerSubscriptionManager subscriptionManager = injector.getInstance(SiriServerSubscriptionManager.class);
    injector.getInstance(StatusServletSource.class);

    /**
     * Handle command line options
     */
    if (cli.hasOption(ARG_ID)) {
      siriClient.setIdentity(cli.getOptionValue(ARG_ID));
      siriServer.setIdentity(cli.getOptionValue(ARG_ID));
    }
    if (cli.hasOption(ARG_CLIENT_URL))
      siriClient.setUrl(cli.getOptionValue(ARG_CLIENT_URL));
    if (cli.hasOption(ARG_PRIVATE_CLIENT_URL))
      siriClient.setPrivateUrl(cli.getOptionValue(ARG_PRIVATE_CLIENT_URL));
    if (cli.hasOption(ARG_REPEATER_URL))
      siriServer.setUrl(cli.getOptionValue(ARG_REPEATER_URL));
    if (cli.hasOption(ARG_PRIVATE_REPEATER_URL))
      siriServer.setPrivateUrl(cli.getOptionValue(ARG_PRIVATE_REPEATER_URL));

    addRequestorConsumerAddressDefaults(cli, subscriptionManager);

    if (cli.hasOption(ARG_LOG_RAW_XML)) {
      String value = cli.getOptionValue(ARG_LOG_RAW_XML);
      ELogRawXmlType type = ELogRawXmlType.valueOf(value.toUpperCase());
      siriClient.setLogRawXmlType(type);
      siriServer.setLogRawXmlType(type);
    }

    siriClient.setFormatOutputXmlByDefault(cli.hasOption(ARG_FORMAT_OUTPUT_XML));
    siriServer.setFormatOutputXmlByDefault(cli.hasOption(ARG_FORMAT_OUTPUT_XML));

    /**
     * Filters
     */
    if (cli.hasOption(ARG_FILTER)) {

      String filterSpec = cli.getOptionValue(ARG_FILTER);
      Map<String, String> filterArgs = SiriLibrary.getLineAsMap(filterSpec);

      SiriModuleDeliveryFilterMatcher matcher = createFilterMatcherForArgs(filterArgs);
      SiriModuleDeliveryFilter filter = createFilterForArgs(filterArgs);

      if (!filterArgs.isEmpty()) {
        List<String> keys = new ArrayList<String>(filterArgs.keySet());
        Collections.sort(keys);
        throw new SiriException(
            "the following filter parameters were unknown: " + keys);
      }

      subscriptionManager.addModuleDeliveryFilter(matcher, filter);
    }

    SiriClientRequestFactory factory = new SiriClientRequestFactory();

    for (String arg : cli.getArgs()) {
      Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
      SiriClientRequest request = factory.createRequest(subArgs);
      siriRepeater.addStartupRequest(request);
    }
  }

  protected SiriModuleDeliveryFilterMatcher createFilterMatcherForArgs(
      Map<String, String> filterArgs) {

    SiriModuleDeliveryFilterMatcherFactoryImpl factory = new SiriModuleDeliveryFilterMatcherFactoryImpl();
    return factory.create(filterArgs);
  }

  protected SiriModuleDeliveryFilter createFilterForArgs(
      Map<String, String> filterArgs) {

    SiriModuleDeliveryFilterFactoryImpl factory = new SiriModuleDeliveryFilterFactoryImpl();
    return factory.create(filterArgs);
  }

  private void addRequestorConsumerAddressDefaults(CommandLine cli,
      SiriServerSubscriptionManager subscriptionManager) {

    if (cli.hasOption(ARG_REQUESTOR_CONSUMER_ADDRESS_DEFAULT)) {

      String[] values = cli.getOptionValues(ARG_REQUESTOR_CONSUMER_ADDRESS_DEFAULT);

      for (String value : values) {
        int index = value.indexOf('=');
        if (index == -1) {
          System.err.println("invalid "
              + ARG_REQUESTOR_CONSUMER_ADDRESS_DEFAULT + " arg: " + value
              + " (expected format: requestorRef=address)");
          printUsage();
          System.exit(-1);
        }

        String requestorRef = value.substring(0, index);
        String consumerAddressDefault = value.substring(index + 1);

        subscriptionManager.setConsumerAddressDefaultForRequestorRef(
            requestorRef, consumerAddressDefault);
      }
    }
  }
}
