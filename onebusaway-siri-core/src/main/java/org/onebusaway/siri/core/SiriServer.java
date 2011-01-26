package org.onebusaway.siri.core;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriRequestResponseHandler;
import org.onebusaway.siri.core.handlers.SiriSubscriptionRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriServer extends SiriCommon implements SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriServer.class);

  private String _identity;

  protected String _serverUrl;

  protected String _privateServerUrl;

  private SiriSubscriptionManager _subscriptionManager = new SiriSubscriptionManager();

  private List<SiriRequestResponseHandler> _requestResponseHandlers = new ArrayList<SiriRequestResponseHandler>();

  private List<SiriSubscriptionRequestHandler> _subscriptionRequestHandlers = new ArrayList<SiriSubscriptionRequestHandler>();

  private ExecutorService _executor;

  public SiriServer() {
    _identity = UUID.randomUUID().toString();
    _serverUrl = "http://localhost:8080";
  }

  public SiriSubscriptionManager getSubscriptionManager() {
    return _subscriptionManager;
  }

  public void setSubscriptionManager(SiriSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  public void setIdentity(String identity) {
    _identity = identity;
  }

  /**
   * The public url our server will listen to and expose to connecting SIRI
   * clients. See also {@link #setPrivateServerUrl(String)}.
   * 
   * @param serverUrl
   * 
   */
  public void setServerUrl(String serverUrl) {
    _serverUrl = serverUrl;
  }

  /**
   * In some cases, we may wish to listen for incoming SIRI client data on a
   * different local URL than the URL we publish externally to SIRI clients (see
   * {@link #setServerUrl(String)}). For example, your firewall or NAT setup
   * might require a separate public and private server url. If set, the
   * privateServerUrl will control how we actually listen for incoming SIRI
   * client connections, separate from the url we announce to clients.
   * 
   * If privateServerUrl is not set, we'll default to using the public serverUrl
   * (see {@link #setServerUrl(String)}).
   * 
   * @param privateServerUrl
   */
  public void setPrivateServerUrl(String privateServerUrl) {
    _privateServerUrl = privateServerUrl;
  }

  public void addRequestResponseHandler(SiriRequestResponseHandler handler) {
    _requestResponseHandlers.add(handler);
  }

  public void removeRequestResponseHandler(SiriRequestResponseHandler handler) {
    _requestResponseHandlers.remove(handler);
  }

  public void addSubscriptionRequestHandler(
      SiriSubscriptionRequestHandler handler) {
    _subscriptionRequestHandlers.add(handler);
  }

  public void removeSubscriptionRequestHandler(
      SiriSubscriptionRequestHandler handler) {
    _subscriptionRequestHandlers.remove(handler);
  }

  public void start() throws SiriException {

    _executor = Executors.newFixedThreadPool(5);
  }

  public void stop() {

    if (_executor != null) {
      _executor.shutdown();
      _executor = null;
    }

    if (_log.isDebugEnabled())
      _log.debug("SiriServer stopped");
  }

  /****
   * 
   ****/

  public void publish(ServiceDelivery serviceDelivery) {

    if (serviceDelivery.getAddress() == null)
      serviceDelivery.setAddress(_serverUrl);

    if (serviceDelivery.getProducerRef() == null) {
      ParticipantRefStructure producerRef = new ParticipantRefStructure();
      producerRef.setValue(_identity);
      serviceDelivery.setProducerRef(producerRef);
    }

    if (serviceDelivery.getResponseTimestamp() == null)
      serviceDelivery.setResponseTimestamp(new Date());

    List<SubscriptionEvent> events = _subscriptionManager.publish(serviceDelivery);

    if (!events.isEmpty()) {

      if (_log.isDebugEnabled())
        _log.debug("SiriPublishEvents=" + events.size());

      for (SubscriptionEvent event : events)
        _executor.submit(new PublishEventTask(event));
    }
  }

  /****
   * {@link SiriRawHandler} Interface
   ****/

  @Override
  public void handleRawRequest(Reader reader, Writer writer) {

    _log.debug("handling request");

    Object request = unmarshall(reader);

    if (request instanceof ServiceRequest) {

      _log.debug("handling ServiceRequest");

      ServiceRequest serviceRequest = (ServiceRequest) request;

      ServiceDelivery response = new ServiceDelivery();

      for (SiriRequestResponseHandler handler : _requestResponseHandlers)
        handler.handleRequestAndResponse(serviceRequest, response);

      ParticipantRefStructure identity = new ParticipantRefStructure();
      identity.setValue(_identity);
      response.setProducerRef(identity);

      if (response.getResponseTimestamp() == null)
        response.setResponseTimestamp(new Date());

      marshall(response, writer);

    } else if (request instanceof SubscriptionRequest) {

      _log.debug("handling SubscriptionRequest");

      SubscriptionRequest subscriptionRequest = (SubscriptionRequest) request;

      for (SiriSubscriptionRequestHandler handler : _subscriptionRequestHandlers)
        handler.handleSubscriptionRequest(subscriptionRequest);

      _subscriptionManager.handleSubscriptionRequest(subscriptionRequest);

    } else {
      _log.warn("unknown siri request " + request);
    }
  }

  /****
   * Private Methods
   ****/

  private void publishResponse(SubscriptionEvent event) {

    SubscriptionDetails details = event.getDetails();
    String address = details.getAddress();
    ServiceDelivery delivery = event.getDelivery();

    try {
      sendHttpRequest(address, delivery);
    } catch (SiriConnectionException ex) {
      _subscriptionManager.terminateSubscription(details);
    }
  }

  private class PublishEventTask implements Runnable {

    private final SubscriptionEvent _event;

    public PublishEventTask(SubscriptionEvent event) {
      _event = event;
    }

    @Override
    public void run() {
      try {
        publishResponse(_event);
      } catch (Throwable ex) {
        SubscriptionDetails details = _event.getDetails();
        _log.warn("error publishing to " + details.getId(), ex);
      }
    }

  }

}
