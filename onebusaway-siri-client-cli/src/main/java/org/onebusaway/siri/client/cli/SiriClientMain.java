package org.onebusaway.siri.client.cli;

import java.io.OutputStreamWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.onebusaway.siri.core.SiriClient;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;

public class SiriClientMain {

  public static void main(String[] args) throws ParseException {

    Options options = new Options();
    buildOptions(options);

    PosixParser parser = new PosixParser();
    CommandLine cli = parser.parse(options, args);

    args = cli.getArgs();

    if (args.length != 1) {
      printUsage();
      System.exit(-1);
    }

    SiriClient client = new SiriClient();
    client.setTargetUrl(args[0]);

    ServiceRequest request = new ServiceRequest();
    ServiceDelivery delivery = client.handlServiceRequestWithResponse(request);

    OutputStreamWriter out = new OutputStreamWriter(System.out);
    client.marshall(delivery, out);
  }

  private static void printUsage() {
    System.err.println("usage: siri-server-url");
  }

  private static void buildOptions(Options options) {

  }
}
