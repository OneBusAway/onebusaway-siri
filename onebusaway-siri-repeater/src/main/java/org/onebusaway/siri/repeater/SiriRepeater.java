package org.onebusaway.siri.repeater;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.jetty.SiriJettyClient;
import org.onebusaway.siri.jetty.SiriJettyServer;

import uk.org.siri.siri.ServiceDelivery;

/**
 * The SIRI repeater create a {@link SiriClient} and a {@link SiriServer} and
 * repeats data received on the client to anyone connected on the server, as
 * appropriate. It's that simple. Want to filter or modify the data passing
 * through the repeater? See the methods available to {@link SiriServer}, as
 * exposed through {@link #getSiriServer()}.
 * 
 * @author bdferris
 */
public class SiriRepeater {

  /**
   * The client is what connects to an existing SIRI data source
   */
  private SiriClient _siriClient = new SiriJettyClient();

  /**
   * The server is what repeats the incoming SIRI data from the source client to
   * other listening clients
   */
  private SiriServer _siriServer = new SiriJettyServer();

  private ClientServiceDeliveryHandler _serviceDeliveryRepeater = new ClientServiceDeliveryHandler();

  private List<SiriClientRequest> _startupRequests = new ArrayList<SiriClientRequest>();

  public SiriClient getSiriClient() {
    return _siriClient;
  }

  public void setSiriClient(SiriClient siriClient) {
    _siriClient = siriClient;
  }

  public SiriServer getSiriServer() {
    return _siriServer;
  }

  public void setSiriServer(SiriServer siriServer) {
    _siriServer = siriServer;
  }

  public void addStartupRequest(SiriClientRequest request) {
    _startupRequests.add(request);
  }

  public void start() {

    /**
     * Register our ServiceDelivery repeater handler with the client
     */
    _siriClient.addServiceDeliveryHandler(_serviceDeliveryRepeater);

    _siriServer.start();
    _siriClient.start();

    for (SiriClientRequest request : _startupRequests)
      _siriClient.handleRequest(request);
  }

  public void stop() {

    _siriServer.stop();
    _siriClient.stop();

    /**
     * Unregister our ServiceDelivery repeater handler
     */
    _siriClient.removeServiceDeliveryHandler(_serviceDeliveryRepeater);
  }

  /**
   * 
   * @author bdferris
   * 
   */
  private class ClientServiceDeliveryHandler implements
      SiriServiceDeliveryHandler {

    @Override
    public void handleServiceDelivery(SiriChannelInfo channelInfo,
        ServiceDelivery serviceDelivery) {
      _siriServer.publish(serviceDelivery);
    }
  }

}
