package org.onebusaway.siri.client.cli;

import java.io.StringWriter;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.SiriRequestFactory;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.jetty.SiriJettyClient;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriClientMain {

  private static final String ARG_SUBSCRIBE = "subscribe";

  private static final String ARG_CLIENT_URL = "clientUrl";

  private static final String ARG_PRIVATE_CLIENT_URL = "privateClientUrl";

  private static final String ARG_ID = "id";

  private SiriJettyClient _client;

  public static void main(String[] args) {

    try {
      SiriClientMain m = new SiriClientMain();
      m.run(args);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
  }

  public void run(String[] args) throws ParseException {

    Options options = new Options();
    buildOptions(options);

    PosixParser parser = new PosixParser();
    CommandLine cli = parser.parse(options, args);

    args = cli.getArgs();

    if (args.length == 0) {
      printUsage();
      System.exit(-1);
    }

    _client = new SiriJettyClient();

    if (cli.hasOption(ARG_ID))
      _client.setIdentity(cli.getOptionValue(ARG_ID));

    if (cli.hasOption(ARG_CLIENT_URL))
      _client.setClientUrl(cli.getOptionValue(ARG_CLIENT_URL));

    if (cli.hasOption(ARG_PRIVATE_CLIENT_URL))
      _client.setPrivateClientUrl(cli.getOptionValue(ARG_PRIVATE_CLIENT_URL));

    SiriRequestFactory factory = new SiriRequestFactory();

    if (cli.hasOption(ARG_SUBSCRIBE)) {

      _client.addServiceDeliveryHandler(new ServiceDeliveryHandlerImpl());

      _client.start();

      for (String arg : args) {
        Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
        SubscriptionRequest request = factory.createSubscriptionRequest(subArgs);

        String url = subArgs.get("Url");
        if (url == null) {
          System.err.println("no Url defined for subscription request: " + arg);
          continue;
        }

        _client.handleSubscriptionRequest(url, request);
      }

    } else {

      for (String arg : args) {
        Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
        ServiceRequest request = factory.createServiceRequest(subArgs);

        String url = subArgs.get("Url");
        if (url == null) {
          System.err.println("no Url defined for subscription request: " + arg);
          continue;
        }

        ServiceDelivery delivery = _client.handleServiceRequestWithResponse(
            url, request);

        printAsXml(delivery);
      }
    }
  }

  private void printAsXml(Object object) {
    StringWriter out = new StringWriter();
    _client.marshall(object, out);
    System.out.println(out.toString());
  }

  private void printUsage() {

    System.err.println("usage:");
    System.err.println("  [-args] request [request ...]");
    System.err.println();
    System.err.println("args:");
    System.err.println("  -" + ARG_ID + "=id                          the client's SIRI participant id");
    System.err.println("  -" + ARG_SUBSCRIBE + "                      indicates that the client should perform a publish/subscribe (default is request/response)");
    System.err.println("  -" + ARG_CLIENT_URL + "=url                  the url your client publishes to a server in publish/subscribe");
    System.err.println("  -" + ARG_PRIVATE_CLIENT_URL + "=url           the internal url your client will actually bind to, if specified (default=clientUrl)");
    System.err.println();
    System.err.println("request examples:");
    System.err.println("  Url=http://host:port/path,ModuleType=VEHICLE_MONITORING");
    System.err.println("  Url=http://host:port/path,ModuleType=VEHICLE_MONITORING,VehicleRef=1234");
  }

  private void buildOptions(Options options) {
    options.addOption(ARG_ID, true, "id");
    options.addOption(ARG_CLIENT_URL, true, "siri client url");
    options.addOption(ARG_PRIVATE_CLIENT_URL, true, "siri private client url");
    options.addOption(ARG_SUBSCRIBE, false, "subscribe (vs one-time request)");
  }

  private class ServiceDeliveryHandlerImpl implements
      SiriServiceDeliveryHandler {

    @Override
    public void handleServiceDelivery(ServiceDelivery serviceDelivery) {
      printAsXml(serviceDelivery);
    }
  }
}
