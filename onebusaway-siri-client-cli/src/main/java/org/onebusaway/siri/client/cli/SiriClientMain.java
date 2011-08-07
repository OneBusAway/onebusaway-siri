package org.onebusaway.siri.client.cli;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriClientRequestFactory;
import org.onebusaway.siri.core.SiriCommon.ELogRawXmlType;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.exceptions.SiriUnknownVersionException;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.subscriptions.client.SiriClientSubscriptionManager;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.siri.jetty.SiriJettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.Siri;

public class SiriClientMain {

  private static final Logger _log = LoggerFactory.getLogger(SiriClientMain.class);

  private static final String ARG_ID = "id";

  private static final String ARG_CLIENT_URL = "clientUrl";

  private static final String ARG_PRIVATE_CLIENT_URL = "privateClientUrl";

  private static final String ARG_OUTPUT = "output";

  private static final String ARG_RESPONSE_TIMEOUT = "responseTimeout";

  private static final String ARG_NO_SUBSCRIPTIONS = "noSubscriptions";

  private static final String ARG_LOG_RAW_XML = "logRawXml";

  private static final String ARG_SUBSCRIBE = "subscribe";

  private static final String ARG_CHECK_STATUS = "checkStatus";

  private static final String ARG_TERMINATE_SUBSCRIPTION = "terminateSubscription";

  private SiriJettyClient _client;

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

    _client = new SiriJettyClient();

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
      SiriClientSubscriptionManager manager = _client.getSubscriptionManager();
      manager.setResponseTimeout(responseTimeout);
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

    _client.addServiceDeliveryHandler(new ServiceDeliveryHandlerImpl());

    _client.start();

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
          return factory.createServiceRequest(subArgs);
        case SUBSCRIPTION:
          return factory.createSubscriptionRequest(subArgs);
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

  private enum ERequestType {
    SERVICE_REQUEST, SUBSCRIPTION, CHECK_STATUS, TERMINATE_SUBSCRIPTION
  }
}
