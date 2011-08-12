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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  
  
  private static final Logger _log = LoggerFactory.getLogger(SiriRepeater.class);

  /**
   * The client is what connects to an existing SIRI data source
   */
  private SiriClient _siriClient;

  /**
   * The server is what repeats the incoming SIRI data from the source client to
   * other listening clients
   */
  private SiriServer _siriServer;

  private ClientServiceDeliveryHandler _serviceDeliveryRepeater = new ClientServiceDeliveryHandler();

  private List<SiriClientRequest> _startupRequests = new ArrayList<SiriClientRequest>();

  @Inject
  public void setClient(SiriClient siriClient) {
    _siriClient = siriClient;
  }

  @Inject
  public void setServer(SiriServer siriServer) {
    _siriServer = siriServer;
  }

  public void addStartupRequest(SiriClientRequest request) {
    _startupRequests.add(request);
  }

  @PostConstruct
  public void start() {

    /**
     * Register our ServiceDelivery repeater handler with the client
     */
    _siriClient.addServiceDeliveryHandler(_serviceDeliveryRepeater);

    /**
     * Fire off our client requests
     */
    for (SiriClientRequest request : _startupRequests)
      _siriClient.handleRequest(request);
  }

  @PreDestroy
  public void stop() {

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
      _log.debug("service delivery");
      _siriServer.publish(serviceDelivery);
    }
  }

}
