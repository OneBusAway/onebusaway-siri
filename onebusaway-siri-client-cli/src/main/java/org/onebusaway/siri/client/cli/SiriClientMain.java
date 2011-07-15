package org.onebusaway.siri.client.cli;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriClientRequestFactory;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.exceptions.SiriUnknownVersionException;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.siri.jetty.SiriJettyClient;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.Siri;

public class SiriClientMain {

  private static final String ARG_ID = "id";

  private static final String ARG_SUBSCRIBE = "subscribe";

  private static final String ARG_TERMINATE_SUBSCRIPTIONS = "terminateSubscriptions";

  private static final String ARG_CLIENT_URL = "clientUrl";

  private static final String ARG_PRIVATE_CLIENT_URL = "privateClientUrl";

  private static final String ARG_OUTPUT = "output";

  private static final String ARG_NO_SUBSCRIPTIONS = "noSubscriptions";

  private static final String ARG_LOG_RAW_XML = "logRawXml";

  private SiriJettyClient _client;

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

  public void run(String[] args) throws Exception {

    Options options = new Options();
    buildOptions(options);

    PosixParser parser = new PosixParser();
    CommandLine cli = parser.parse(options, args);

    args = cli.getArgs();

    _client = new SiriJettyClient();

    if (cli.hasOption(ARG_ID))
      _client.setIdentity(cli.getOptionValue(ARG_ID));

    if (cli.hasOption(ARG_CLIENT_URL))
      _client.setClientUrl(cli.getOptionValue(ARG_CLIENT_URL));

    if (cli.hasOption(ARG_PRIVATE_CLIENT_URL))
      _client.setPrivateClientUrl(cli.getOptionValue(ARG_PRIVATE_CLIENT_URL));

    if (cli.hasOption(ARG_LOG_RAW_XML))
      _client.setLogRawXml(true);

    if (cli.hasOption(ARG_OUTPUT)) {
      String value = cli.getOptionValue(ARG_OUTPUT);
      _output = new PrintWriter(new FileWriter(value));
    } else {
      _output = new PrintWriter(System.out);
    }

    if (args.length == 0 && !cli.hasOption(ARG_NO_SUBSCRIPTIONS)) {
      printUsage();
      System.exit(-1);
    }

    /**
     * Register a shutdown hook to clean up the client when things shutdown
     */
    Thread shutdownHook = new Thread(new ShutdownHookRunnable());
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    _client.start();

    SiriClientRequestFactory factory = new SiriClientRequestFactory();

    if (cli.hasOption(ARG_TERMINATE_SUBSCRIPTIONS)) {

      _client.addServiceDeliveryHandler(new ServiceDeliveryHandlerImpl());

      for (String arg : args) {
        SiriClientRequest request = getLineAsTerminateSubscriptionRequest(
            factory, arg);
        _client.handleRequest(request);
      }

    } else if (cli.hasOption(ARG_SUBSCRIBE)) {

      _client.addServiceDeliveryHandler(new ServiceDeliveryHandlerImpl());

      for (String arg : args) {
        SiriClientRequest request = getLineAsSubscriptionRequest(factory, arg);
        _client.handleRequest(request);
      }

    } else {

      for (String arg : args) {

        SiriClientRequest request = getLineAsServiceRequest(factory, arg);
        Siri delivery = _client.handleRequestWithResponse(request);

        printAsXml(delivery);
      }
    }
  }

  /****
   * Private Methods
   ****/

  private SiriClientRequest getLineAsSubscriptionRequest(
      SiriClientRequestFactory factory, String arg) {

    try {
      Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
      return factory.createSubscriptionRequest(subArgs);
    } catch (SiriUnknownVersionException ex) {
      handleUnknownSiriVersion(arg, ex);
    }

    return null;
  }

  private SiriClientRequest getLineAsTerminateSubscriptionRequest(
      SiriClientRequestFactory factory, String arg) {

    try {
      Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
      return factory.createTerminateSubscriptionRequest(subArgs);
    } catch (SiriUnknownVersionException ex) {
      handleUnknownSiriVersion(arg, ex);
    }

    return null;
  }

  private SiriClientRequest getLineAsServiceRequest(
      SiriClientRequestFactory factory, String arg) {

    try {
      Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
      return factory.createServiceRequest(subArgs);
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

  private void printAsXml(Object object) {
    StringWriter out = new StringWriter();
    _client.marshall(object, out);
    _output.println(out.toString());
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
    options.addOption(ARG_SUBSCRIBE, false, "subscribe (vs one-time request)");
    options.addOption(ARG_TERMINATE_SUBSCRIPTIONS, false,
        "terminate the specified subscriptions");
    options.addOption(ARG_NO_SUBSCRIPTIONS, false, "");
    options.addOption(ARG_LOG_RAW_XML, false, "log raw xml");
  }

  private class ServiceDeliveryHandlerImpl implements
      SiriServiceDeliveryHandler {

    @Override
    public void handleServiceDelivery(SiriChannelInfo channelInfo,
        ServiceDelivery serviceDelivery) {
      Siri siri = new Siri();
      siri.setServiceDelivery(serviceDelivery);
      printAsXml(siri);
    }
  }

  private class ShutdownHookRunnable implements Runnable {

    @Override
    public void run() {
      _client.stop();

      if (_output != null) {
        _output.close();
        _output = null;
      }
    }
  }

}
