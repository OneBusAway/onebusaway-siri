package org.onebusaway.siri.core;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.xml.datatype.Duration;

import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriRequestResponseHandler;
import org.onebusaway.siri.core.handlers.SiriSubscriptionRequestHandler;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.MessageRefStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionContextStructure;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriServer extends SiriCommon implements SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriServer.class);

  private String _identity;

  protected String _serverUrl;

  protected String _privateServerUrl;

  private SiriSubscriptionManager _subscriptionManager = new SiriSubscriptionManager();

  private List<SiriRequestResponseHandler> _requestResponseHandlers = new ArrayList<SiriRequestResponseHandler>();

  private List<SiriSubscriptionRequestHandler> _subscriptionRequestHandlers = new ArrayList<SiriSubscriptionRequestHandler>();

  private ConcurrentMap<String, ScheduledFuture<?>> _heartbeatTasksByMessageId = new ConcurrentHashMap<String, ScheduledFuture<?>>();

  private ScheduledExecutorService _executor;

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

    _executor = Executors.newScheduledThreadPool(5);
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

    fillInServiceDeliveryDefaults(serviceDelivery);

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

    Object data = unmarshall(reader);

    /**
     * Make sure the incoming SIRI data
     */
    SiriVersioning versioning = SiriVersioning.getInstance();
    data = versioning.getPayloadAsVersion(data, versioning.getDefaultVersion());

    if (data instanceof Siri) {

      Siri siri = (Siri) data;

      ServiceRequest serviceRequest = siri.getServiceRequest();
      if (serviceRequest != null)
        handleServiceRequest(serviceRequest, writer);

      SubscriptionRequest subscriptionRequest = siri.getSubscriptionRequest();
      if (subscriptionRequest != null)
        handleSubscriptionRequest(subscriptionRequest);

    } else if (data instanceof ServiceRequest) {

      ServiceRequest serviceRequest = (ServiceRequest) data;
      handleServiceRequest(serviceRequest, writer);

    } else if (data instanceof SubscriptionRequest) {

      SubscriptionRequest subscriptionRequest = (SubscriptionRequest) data;
      handleSubscriptionRequest(subscriptionRequest);

    } else {
      _log.warn("unknown siri request " + data);
    }
  }

  /****
   * Private Methods
   ****/

  private void handleSubscriptionRequest(SubscriptionRequest subscriptionRequest) {

    _log.debug("handling SubscriptionRequest");
    for (SiriSubscriptionRequestHandler handler : _subscriptionRequestHandlers)
      handler.handleSubscriptionRequest(subscriptionRequest);

    SubscriptionDetails details = _subscriptionManager.handleSubscriptionRequest(subscriptionRequest);

    SubscriptionContextStructure context = subscriptionRequest.getSubscriptionContext();

    if (context != null && context.getHeartbeatInterval() != null) {
      Duration interval = context.getHeartbeatInterval();
      long heartbeatInterval = interval.getTimeInMillis(new Date());

      HeartbeatTask task = new HeartbeatTask(details);
      ScheduledFuture<?> future = _executor.scheduleAtFixedRate(task,
          heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
      _heartbeatTasksByMessageId.put(details.getMessageId(), future);
    }
  }

  private void handleServiceRequest(ServiceRequest serviceRequest, Writer writer) {

    _log.debug("handling ServiceRequest");

    ServiceDelivery response = new ServiceDelivery();

    for (SiriRequestResponseHandler handler : _requestResponseHandlers)
      handler.handleRequestAndResponse(serviceRequest, response);

    ParticipantRefStructure identity = new ParticipantRefStructure();
    identity.setValue(_identity);
    response.setProducerRef(identity);

    if (response.getResponseTimestamp() == null)
      response.setResponseTimestamp(new Date());

    marshall(response, writer);
  }

  private void fillInServiceDeliveryDefaults(ServiceDelivery serviceDelivery) {

    if (serviceDelivery.getAddress() == null)
      serviceDelivery.setAddress(_serverUrl);

    if (serviceDelivery.getProducerRef() == null) {
      ParticipantRefStructure producerRef = new ParticipantRefStructure();
      producerRef.setValue(_identity);
      serviceDelivery.setProducerRef(producerRef);
    }

    if (serviceDelivery.getResponseTimestamp() == null)
      serviceDelivery.setResponseTimestamp(new Date());

    if (serviceDelivery.getResponseMessageIdentifier() == null) {
      MessageQualifierStructure messageId = new MessageQualifierStructure();
      messageId.setValue(UUID.randomUUID().toString());
      serviceDelivery.setResponseMessageIdentifier(messageId);
    }
  }

  private void publishResponse(SubscriptionEvent event) {

    ModuleSubscriptionDetails moduleDetails = event.getDetails();
    SubscriptionDetails details = moduleDetails.getDetails();
    String address = details.getAddress();
    ServiceDelivery delivery = event.getDelivery();

    try {
      sendHttpRequest(address, delivery);
    } catch (SiriConnectionException ex) {
      terminateSubscription(details);
    }
  }

  private void terminateSubscription(SubscriptionDetails details) {
    _subscriptionManager.terminateSubscription(details);
    ScheduledFuture<?> future = _heartbeatTasksByMessageId.remove(details.getMessageId());
    if (future != null)
      future.cancel(true);
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
        ModuleSubscriptionDetails details = _event.getDetails();
        _log.warn("error publishing to " + details.getId(), ex);
      }
    }
  }

  private class HeartbeatTask implements Runnable {

    private final SubscriptionDetails _details;

    public HeartbeatTask(SubscriptionDetails details) {
      _details = details;
    }

    @Override
    public void run() {

      String address = _details.getAddress();

      ServiceDelivery serviceDelivery = new ServiceDelivery();
      fillInServiceDeliveryDefaults(serviceDelivery);

      String messageId = _details.getMessageId();
      if (messageId != null) {
        MessageRefStructure messageRef = new MessageRefStructure();
        messageRef.setValue(messageId);
        serviceDelivery.setRequestMessageRef(messageRef);
      }

      try {

        _log.debug("publishing heartbeat to " + address);
        sendHttpRequest(address, serviceDelivery);

      } catch (SiriConnectionException ex) {
        _log.warn("error publishing heartbeat to " + address, ex);
        terminateSubscription(_details);
      }
    }
  }

}
