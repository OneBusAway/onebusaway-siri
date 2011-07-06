package org.onebusaway.siri.repeater;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.onebusaway.cli.Daemonizer;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriClientRequestFactory;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.SiriSubscriptionManager;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterFactoryImpl;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterMatcher;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterMatcherFactoryImpl;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterSource;
import org.onebusaway.siri.jetty.SiriJettyClient;
import org.onebusaway.siri.jetty.SiriJettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

public class SiriRepeaterCommandLineConfiguration {

  private static final Logger _log = LoggerFactory.getLogger(SiriRepeaterCommandLineConfiguration.class);

  private static final String ARG_ID = "id";

  private static final String ARG_REPEATER_URL = "repeaterUrl";

  private static final String ARG_PRIVATE_REPEATER_URL = "privateRepeaterUrl";

  private static final String ARG_CLIENT_URL = "clientUrl";

  private static final String ARG_FILTER = "filter";

  private static final String ARG_PRIVATE_CLIENT_URL = "privateClientUrl";

  private static final String ARG_REQUESTOR_CONSUMER_ADDRESS_DEFAULT = "requestorConsumerAddressDefault";

  private static final String ARG_LOG_RAW_XML = "logRawXml";

  private static final String ARG_NO_SUBSCRIPTIONS = "noSubscriptions";

  private static final String ARG_DATA_SOURCE = "dataSource";

  private static final String CLASSPATH_PREFIX = "classpath:";

  private static final String FILE_PREFIX = "file:";

  public SiriRepeater configure(String[] args) throws Exception {

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

    List<String> paths = new ArrayList<String>();

    if (cli.hasOption(ARG_DATA_SOURCE))
      paths.add(cli.getOptionValue(ARG_DATA_SOURCE));

    paths.add(CLASSPATH_PREFIX
        + "org/onebusaway/siri/repeater/application-context.xml");

    ConfigurableApplicationContext context = createContext(cli, paths);

    handleCommandLineOptions(cli, context);

    SiriRepeater siriRepeater = context.getBean(SiriRepeater.class);
    SiriClient client = siriRepeater.getSiriClient();
    SiriServer server = siriRepeater.getSiriServer();

    jointlyConfigureClientAndServerWebapp(client, server);

    SiriClientRequestFactory factory = new SiriClientRequestFactory();

    for (String arg : args) {
      Map<String, String> subArgs = SiriLibrary.getLineAsMap(arg);
      SiriClientRequest request = factory.createSubscriptionRequest(subArgs);
      siriRepeater.addStartupRequest(request);
    }

    return siriRepeater;
  }

  private boolean needsHelp(String[] args) {
    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help") || arg.equals("-help"))
        return true;
    }
    return false;
  }

  private void printUsage() {
    System.err.println("usage:");
    System.err.println("  [-args] request [request ...]");
    System.err.println();
    System.err.println("args:");
    System.err.println("  -"
        + ARG_ID
        + "=id                          the SIRI participant id used by your client and server");
    System.err.println("  -"
        + ARG_CLIENT_URL
        + "=url                  the url your repeater client binds to and uses in publish/subscribe with your SIRI source");
    System.err.println("  -"
        + ARG_PRIVATE_CLIENT_URL
        + "=url           the internal url your repeater client will actually bind to, if specifed (default=clientUrl)");
    System.err.println("  -"
        + ARG_REPEATER_URL
        + "=url                the url your repeater server binds to and listens to incoming client requests");
    System.err.println("  -"
        + ARG_PRIVATE_REPEATER_URL
        + "=url         the internal url your repeater server will actually bind to, if specified (default=repeaterUrl)");
    System.err.println("  -"
        + ARG_DATA_SOURCE
        + "=path                a Spring context.xml file containing additional bean defs");
    System.err.println();
    System.err.println("request examples:");
    System.err.println("  Url=http://host:port/path,ModuleType=VEHICLE_MONITORING");
    System.err.println("  Url=http://host:port/path,ModuleType=VEHICLE_MONITORING,VehicleRef=1234");
  }

  protected void buildOptions(Options options) {
    options.addOption(ARG_ID, true, "SIRI client participant id");
    options.addOption(ARG_REPEATER_URL, true, "repeater url");
    options.addOption(ARG_PRIVATE_REPEATER_URL, true, "private repeater url");
    options.addOption(ARG_CLIENT_URL, true, "client url");
    options.addOption(ARG_PRIVATE_CLIENT_URL, true, "private client url");
    options.addOption(ARG_REQUESTOR_CONSUMER_ADDRESS_DEFAULT, true,
        "consumer address default for requestor");
    options.addOption(ARG_LOG_RAW_XML, false, "log raw xml");
    options.addOption(ARG_NO_SUBSCRIPTIONS, false, "no subscriptions");
    options.addOption(ARG_FILTER, true, "filter specification");
    options.addOption(ARG_DATA_SOURCE, true, "Spring data source xml file");

  }

  protected void handleCommandLineOptions(CommandLine cli,
      ConfigurableApplicationContext context) {

    SiriRepeater siriRepeater = context.getBean(SiriRepeater.class);

    SiriClient siriClient = siriRepeater.getSiriClient();
    SiriServer siriServer = siriRepeater.getSiriServer();
    SiriSubscriptionManager subscriptionManager = siriServer.getSubscriptionManager();

    /**
     * Handle command line options
     */
    if (cli.hasOption(ARG_ID)) {
      siriClient.setIdentity(cli.getOptionValue(ARG_ID));
      siriServer.setIdentity(cli.getOptionValue(ARG_ID));
    }
    if (cli.hasOption(ARG_CLIENT_URL))
      siriClient.setClientUrl(cli.getOptionValue(ARG_CLIENT_URL));
    if (cli.hasOption(ARG_PRIVATE_CLIENT_URL))
      siriClient.setPrivateClientUrl(cli.getOptionValue(ARG_PRIVATE_CLIENT_URL));
    if (cli.hasOption(ARG_REPEATER_URL))
      siriServer.setServerUrl(cli.getOptionValue(ARG_REPEATER_URL));
    if (cli.hasOption(ARG_PRIVATE_REPEATER_URL))
      siriServer.setPrivateServerUrl(cli.getOptionValue(ARG_PRIVATE_REPEATER_URL));

    addRequestorConsumerAddressDefaults(cli, subscriptionManager);

    if (cli.hasOption(ARG_LOG_RAW_XML))
      siriClient.setLogRawXml(true);

    /**
     * Filters
     */
    Map<String, SiriModuleDeliveryFilterSource> filterSources = context.getBeansOfType(SiriModuleDeliveryFilterSource.class);
    for (SiriModuleDeliveryFilterSource filterSource : filterSources.values())
      subscriptionManager.addModuleDeliveryFilter(filterSource.getMatcher(),
          filterSource.getFilter());

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
  }

  protected void jointlyConfigureClientAndServerWebapp(SiriClient client,
      SiriServer server) {

    if (client instanceof SiriJettyClient && server instanceof SiriJettyServer) {

      SiriJettyClient jettyClient = (SiriJettyClient) client;
      SiriJettyServer jettyServer = (SiriJettyServer) server;

      URL clientUrl = jettyClient.getInternalUrlToBind();
      URL serverUrl = jettyServer.getInternalUrlToBind();

      if (clientUrl.getPort() == serverUrl.getPort()) {

        String clientPath = clientUrl.getPath();
        String serverPath = serverUrl.getPath();
        if (clientPath.equals(serverPath)) {
          _log.error("The SIRI repeater client and server are configured to listen to the same url, which is not allowed: "
              + clientUrl + " vs " + serverUrl);
          System.exit(-1);
        }

        Server webServer = new Server(clientUrl.getPort());
        jettyClient.setWebServer(webServer);
        jettyServer.setWebServer(webServer);

        Context rootContext = new Context(webServer, "/", Context.SESSIONS);
        jettyClient.setRootContext(rootContext);
        jettyServer.setRootContext(rootContext);
      }
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
      SiriSubscriptionManager subscriptionManager) {

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

  protected ConfigurableApplicationContext createContext(CommandLine cli,
      Iterable<String> paths) {

    Map<String, BeanDefinition> additionalBeans = new HashMap<String, BeanDefinition>();

    GenericApplicationContext ctx = new GenericApplicationContext();
    XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(ctx);

    for (String path : paths) {
      if (path.startsWith(CLASSPATH_PREFIX)) {
        path = path.substring(CLASSPATH_PREFIX.length());
        xmlReader.loadBeanDefinitions(new ClassPathResource(path));
      } else if (path.startsWith(FILE_PREFIX)) {
        path = path.substring(FILE_PREFIX.length());
        xmlReader.loadBeanDefinitions(new FileSystemResource(path));
      } else {
        xmlReader.loadBeanDefinitions(new ClassPathResource(path));
      }
    }

    for (Map.Entry<String, BeanDefinition> entry : additionalBeans.entrySet())
      ctx.registerBeanDefinition(entry.getKey(), entry.getValue());

    ctx.refresh();
    ctx.registerShutdownHook();
    return ctx;
  }
}
