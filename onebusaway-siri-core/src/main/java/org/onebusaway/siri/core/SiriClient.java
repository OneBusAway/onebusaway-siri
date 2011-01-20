package org.onebusaway.siri.core;

import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.exceptions.SiriSerializationException;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriClient extends SiriCommon implements SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriClient.class);

  private String _identity;

  private String _clientUrl;

  private String _privateClientUrl;

  private Server _webServer;

  private List<SiriServiceDeliveryHandler> _serviceDeliveryHandlers = new ArrayList<SiriServiceDeliveryHandler>();

  public SiriClient() {
    _identity = UUID.randomUUID().toString();
    _clientUrl = "http://localhost:8081/";
  }

  public void setIdentity(String identity) {
    _identity = identity;
  }

  /**
   * The public url our client will listen to and expose for url callbacks from
   * the SIRI server. Only used when using publish / subscribe methods. See also
   * {@link #setPrivateClientUrl(String)}.
   * 
   * @param clientUrl
   * 
   */
  public void setClientUrl(String clientUrl) {
    _clientUrl = clientUrl;
  }

  /**
   * In some cases, we may wish to listen for incoming SIRI data from the server
   * on a different local URL than the URL we publish externally to the SIRI
   * server (see {@link #setClientUrl(String)}). For example, your firewall or
   * NAT setup might require a separate public and private client url. If set,
   * the privateClientUrl will control how we actually listen for incoming SIRI
   * service deliveries, separate from the url we announce to the server.
   * 
   * If privateClientUrl is not set, we'll default to using the public clientUrl
   * (see {@link #setClientUrl(String)}).
   * 
   * @param privateClientUrl
   */
  public void setPrivateClientUrl(String privateClientUrl) {
    _privateClientUrl = privateClientUrl;
  }

  public void addServiceDeliveryHandler(SiriServiceDeliveryHandler handler) {
    _serviceDeliveryHandlers.add(handler);
  }

  public void removeServiceDeliveryHandler(SiriServiceDeliveryHandler handler) {
    _serviceDeliveryHandlers.remove(handler);
  }
  
  /****
   * 
   ****/

  public void start() {

    SubscriptionServerServlet servlet = new SubscriptionServerServlet();
    servlet.setSiriListener(this);

    String clientUrl = _clientUrl;
    if (_privateClientUrl != null)
      clientUrl = _privateClientUrl;

    URL url = url(clientUrl);

    _webServer = new Server(url.getPort());

    Context root = new Context(_webServer, "/", Context.SESSIONS);
    root.addServlet(new ServletHolder(servlet), "/*");

    try {
      _webServer.start();
    } catch (Exception ex) {
      throw new SiriException("error starting SiriServer", ex);
    }
  }

  public void stop() {
    if (_webServer != null) {
      try {
        _webServer.stop();
      } catch (Exception ex) {
        _log.warn("error stoping SiriServer", ex);
      }
      _webServer = null;
    }
  }

  /****
   * Primary Client Methods
   ****/

  /**
   * 
   * @param taretUrl the target server url to connect to
   * @param request the service request
   * @return the immediate service delivery received from the server
   */
  public ServiceDelivery handleServiceRequestWithResponse(String taretUrl,
      ServiceRequest request) {

    ParticipantRefStructure identity = new ParticipantRefStructure();
    identity.setValue(_identity);
    request.setRequestorRef(identity);

    request.setAddress(_clientUrl);

    MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
    messageIdentifier.setValue(UUID.randomUUID().toString());
    request.setMessageIdentifier(messageIdentifier);

    request.setRequestTimestamp(new Date());

    HttpResponse response = sendHttpRequest(taretUrl, request);

    HttpEntity entity = response.getEntity();
    try {
      return unmarshall(entity.getContent());
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }
  }

  /**
   * 
   * @param targetUrl the target server url to connect to
   * @param request the subscription request
   */
  public void handleSubscriptionRequest(String targetUrl,
      SubscriptionRequest request) {

    ParticipantRefStructure identity = new ParticipantRefStructure();
    identity.setValue(_identity);
    request.setRequestorRef(identity);

    request.setAddress(_clientUrl.toString());

    MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
    messageIdentifier.setValue(UUID.randomUUID().toString());
    request.setMessageIdentifier(messageIdentifier);

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {
      List<AbstractSubscriptionStructure> requests = SiriLibrary.getSubscriptionRequestsForModule(
          request, moduleType);
      
      for (AbstractSubscriptionStructure subRequest : requests) {

        SubscriptionQualifierStructure subId = subRequest.getSubscriptionIdentifier();
        if (subId == null) {
          subId = new SubscriptionQualifierStructure();
          subId.setValue(UUID.randomUUID().toString());
          subRequest.setSubscriptionIdentifier(subId);
        }
      }
    }

    sendHttpRequest(targetUrl, request);
  }

  /*****
   * {@link SiriRawHandler} Interface
   ****/

  @Override
  public void handleRawRequest(Reader reader, Writer writer) {
    Object data = unmarshall(reader);

    if (data instanceof ServiceDelivery) {
      ServiceDelivery delivery = (ServiceDelivery) data;
      for (SiriServiceDeliveryHandler handler : _serviceDeliveryHandlers)
        handler.handleServiceDelivery(delivery);
    }
  }
}
