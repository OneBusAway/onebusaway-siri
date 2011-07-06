package org.onebusaway.siri.core;

import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.HeartbeatNotificationStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.MessageRefStructure;
import uk.org.siri.siri.ResponseStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionContextStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;

public class SiriServer extends SiriCommon implements SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriServer.class);

  private String _identity;

  protected String _serverUrl;

  protected String _privateServerUrl;

  private SiriSubscriptionManager _subscriptionManager = new SiriSubscriptionManager();

  private List<SiriRequestResponseHandler> _requestResponseHandlers = new ArrayList<SiriRequestResponseHandler>();

  private List<SiriSubscriptionRequestHandler> _subscriptionRequestHandlers = new ArrayList<SiriSubscriptionRequestHandler>();

  private ScheduledExecutorService _executor;

  private long _serviceStartedTimestamp;

  public SiriServer() {
    _identity = UUID.randomUUID().toString();
    _serverUrl = replaceHostnameWildcardWithPublicHostnameInUrl("http://*:8080/server.xml");
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
   * See also {@link #getPrivateServerUrl()}.
   * 
   * @return the public url our server will listen to and expose to connecting
   *         SIRI clients.
   */
  public String getServerUrl() {
    return _serverUrl;
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
   * See discussion in {@link #setPrivateServerUrl(String)}.
   * 
   * @return if set, the internal url our server will actually listen to
   */
  public String getPrivateServerUrl() {
    return _privateServerUrl;
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

  /**
   * The internal URL that the server should bind to. This defaults to
   * {@link #getServerUrl()}, unless {@link #getPrivateServerUrl()} has been
   * specified.
   * 
   * @return
   */
  public URL getInternalUrlToBind() {
    String serverUrl = _serverUrl;
    if (_privateServerUrl != null)
      serverUrl = _privateServerUrl;
    return url(serverUrl);
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
    _serviceStartedTimestamp = System.currentTimeMillis();
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

  /**
   * @return **
   * 
   ****/

  public int publish(ServiceDelivery serviceDelivery) {

    fillInServiceDeliveryDefaults(serviceDelivery);

    List<ServerSubscriptionEvent> events = _subscriptionManager.publish(serviceDelivery);

    if (!events.isEmpty()) {

      if (_log.isDebugEnabled())
        _log.debug("SiriPublishEvents=" + events.size());

      for (ServerSubscriptionEvent event : events)
        _executor.submit(new PublishEventTask(event));
    }

    return events.size();
  }

  /****
   * {@link SiriRawHandler} Interface
   ****/

  @Override
  public void handleRawRequest(Reader reader, Writer writer) {

    _log.debug("handling request");

    Object data = unmarshall(reader);

    /**
     * Make sure the incoming SIRI data is updated to the latest version
     */
    SiriVersioning versioning = SiriVersioning.getInstance();
    ESiriVersion originalVersion = versioning.getVersionOfObject(data);
    data = versioning.getPayloadAsVersion(data, versioning.getDefaultVersion());

    if (data instanceof Siri) {

      Siri siri = (Siri) data;
      Siri siriResponse = new Siri();

      ServiceRequest serviceRequest = siri.getServiceRequest();
      if (serviceRequest != null) {
        ServiceDelivery serviceDelivery = handleServiceRequest(serviceRequest);
        siriResponse.setServiceDelivery(serviceDelivery);
      }

      SubscriptionRequest subscriptionRequest = siri.getSubscriptionRequest();
      if (subscriptionRequest != null) {
        SubscriptionResponseStructure subscriptionResponse = handleSubscriptionRequest(
            subscriptionRequest, originalVersion);
        siriResponse.setSubscriptionResponse(subscriptionResponse);
      }

      CheckStatusRequestStructure checkStatusRequest = siri.getCheckStatusRequest();
      if (checkStatusRequest != null) {
        CheckStatusResponseStructure response = handleCheckStatusRequest(checkStatusRequest);
        siriResponse.setCheckStatusResponse(response);
      }

      TerminateSubscriptionRequestStructure terminateSubscriptionRequest = siri.getTerminateSubscriptionRequest();
      if (terminateSubscriptionRequest != null) {
        TerminateSubscriptionResponseStructure response = handleTerminateSubscriptionRequest(terminateSubscriptionRequest);
        siriResponse.setTerminateSubscriptionResponse(response);
      }

      /**
       * Send the (properly versioned) response
       */
      Object responseData = versioning.getPayloadAsVersion(siriResponse,
          originalVersion);
      marshall(responseData, writer);

    } else if (data instanceof ServiceRequest) {

      ServiceRequest serviceRequest = (ServiceRequest) data;
      ServiceDelivery response = handleServiceRequest(serviceRequest);

      /**
       * Send the (properly versioned) response
       */
      Object responseData = versioning.getPayloadAsVersion(response,
          originalVersion);
      marshall(responseData, writer);

    } else if (data instanceof SubscriptionRequest) {

      SubscriptionRequest subscriptionRequest = (SubscriptionRequest) data;
      SubscriptionResponseStructure response = handleSubscriptionRequest(
          subscriptionRequest, originalVersion);

      Siri siriResponse = new Siri();
      siriResponse.setSubscriptionResponse(response);

      /**
       * Send the (properly versioned) response
       */
      Object responseData = versioning.getPayloadAsVersion(siriResponse,
          originalVersion);
      marshall(responseData, writer);

    } else {
      _log.warn("unknown siri request " + data);
    }
  }

  /****
   * Private Methods
   * 
   * @return
   ****/

  private SubscriptionResponseStructure handleSubscriptionRequest(
      SubscriptionRequest subscriptionRequest, ESiriVersion originalVersion) {

    _log.debug("handling SubscriptionRequest");
    SubscriptionResponseStructure response = new SubscriptionResponseStructure();

    for (SiriSubscriptionRequestHandler handler : _subscriptionRequestHandlers)
      handler.handleSubscriptionRequest(subscriptionRequest);

    ServerSubscriptionChannel channel = _subscriptionManager.handleSubscriptionRequest(
        subscriptionRequest, originalVersion);

    applySubscriptionContext(subscriptionRequest, channel);

    response.setResponseTimestamp(new Date());
    response.setResponderRef(SiriTypeFactory.particpantRef(_identity));
    response.setServiceStartedTime(new Date(_serviceStartedTimestamp));

    MessageQualifierStructure messageId = subscriptionRequest.getMessageIdentifier();
    response.setRequestMessageRef(messageId);

    return response;
  }

  private void applySubscriptionContext(
      SubscriptionRequest subscriptionRequest, ServerSubscriptionChannel channel) {

    synchronized (channel) {

      SubscriptionContextStructure context = subscriptionRequest.getSubscriptionContext();
      if (context == null)
        return;

      long heartbeatInterval = 0;
      Duration interval = context.getHeartbeatInterval();
      if (interval != null)
        heartbeatInterval = interval.getTimeInMillis(new Date());

      if (channel.getHeartbeatInterval() != heartbeatInterval) {
        channel.setHeartbeatInterval(heartbeatInterval);
        ScheduledFuture<?> task = channel.getHeartbeatTask();
        if (task != null) {
          task.cancel(true);
          channel.setHeartbeatTask(null);
        }

        if (heartbeatInterval > 0) {
          HeartbeatTask heartbeatTask = new HeartbeatTask(channel);
          task = _executor.scheduleAtFixedRate(heartbeatTask,
              heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
          channel.setHeartbeatTask(task);
        }
      }
    }
  }

  private ServiceDelivery handleServiceRequest(ServiceRequest serviceRequest) {
    ServiceDelivery response = new ServiceDelivery();

    for (SiriRequestResponseHandler handler : _requestResponseHandlers)
      handler.handleRequestAndResponse(serviceRequest, response);

    fillInServiceDeliveryDefaults(response);
    return response;
  }

  private CheckStatusResponseStructure handleCheckStatusRequest(
      CheckStatusRequestStructure checkStatusRequest) {

    CheckStatusResponseStructure response = new CheckStatusResponseStructure();
    response.setStatus(Boolean.TRUE);
    response.setAddress(_serverUrl);
    response.setProducerRef(SiriTypeFactory.particpantRef(_identity));
    response.setResponseMessageIdentifier(SiriTypeFactory.randomMessageId());

    MessageQualifierStructure messageId = checkStatusRequest.getMessageIdentifier();
    if (messageId != null) {
      MessageRefStructure ref = new MessageRefStructure();
      ref.setValue(messageId.getValue());
      response.setRequestMessageRef(ref);
    }

    response.setServiceStartedTime(new Date(_serviceStartedTimestamp));

    fillInResponseStructure(response);

    return response;
  }

  private TerminateSubscriptionResponseStructure handleTerminateSubscriptionRequest(
      TerminateSubscriptionRequestStructure request) {

    TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();

    return response;

  }

  /****
   * 
   ****/

  /**
   * 
   * @param serviceDelivery
   */
  private void fillInServiceDeliveryDefaults(ServiceDelivery serviceDelivery) {

    if (serviceDelivery.getAddress() == null)
      serviceDelivery.setAddress(_serverUrl);

    if (serviceDelivery.getProducerRef() == null) {
      serviceDelivery.setProducerRef(SiriTypeFactory.particpantRef(_identity));
    }

    if (serviceDelivery.getResponseMessageIdentifier() == null) {
      MessageQualifierStructure messageId = SiriTypeFactory.randomMessageId();
      serviceDelivery.setResponseMessageIdentifier(messageId);
    }

    fillInResponseStructure(serviceDelivery);
  }

  private void fillInResponseStructure(ResponseStructure response) {
    if (response.getResponseTimestamp() == null)
      response.setResponseTimestamp(new Date());
  }

  /****
   * 
   ****/

  private void publishResponse(ServerSubscriptionEvent event) {

    ServerSubscriptionInstance instance = event.getInstance();
    ServerSubscriptionChannel channel = instance.getChannel();

    ServiceDelivery delivery = event.getDelivery();

    String address = channel.getConsumerAddress();

    Siri siri = new Siri();
    siri.setServiceDelivery(delivery);

    /**
     * Make sure the outgoing SIRI data is updated to the client version
     */
    SiriVersioning versioning = SiriVersioning.getInstance();
    ESiriVersion originalVersion = channel.getTargetVersion();
    Object data = versioning.getPayloadAsVersion(siri, originalVersion);

    try {
      sendHttpRequest(address, data);
    } catch (SiriConnectionException ex) {
      _log.warn("error connecting to client at " + address);
      terminateSubscriptionInstance(instance);
    }
  }

  private void terminateChannelSubscription(ServerSubscriptionChannel channel) {

    synchronized (channel) {
      _subscriptionManager.terminateSubscriptionChannel(channel);

      clearHeartbeatTask(channel);
    }
  }

  private void terminateSubscriptionInstance(ServerSubscriptionInstance instance) {

    _log.debug("terminating subscription instance: {}", instance);

    ServerSubscriptionChannel channel = instance.getChannel();

    synchronized (channel) {

      boolean terminateChannel = _subscriptionManager.terminateSubscriptionInstance(instance);

      if (terminateChannel)
        clearHeartbeatTask(channel);
    }
  }

  private void clearHeartbeatTask(ServerSubscriptionChannel channel) {
    ScheduledFuture<?> heartbeatTask = channel.getHeartbeatTask();
    if (heartbeatTask != null) {
      heartbeatTask.cancel(true);
      channel.setHeartbeatTask(null);
    }
  }

  /****
   * 
   ****/

  private class PublishEventTask implements Runnable {

    private final ServerSubscriptionEvent _event;

    public PublishEventTask(ServerSubscriptionEvent event) {
      _event = event;
    }

    @Override
    public void run() {
      try {
        publishResponse(_event);
      } catch (Throwable ex) {
        ServerSubscriptionInstance instance = _event.getInstance();
        _log.warn("error publishing to " + instance, ex);
        terminateSubscriptionInstance(instance);
      }
    }
  }

  private class HeartbeatTask implements Runnable {

    private final ServerSubscriptionChannel _channel;

    public HeartbeatTask(ServerSubscriptionChannel channel) {
      _channel = channel;
    }

    @Override
    public void run() {

      HeartbeatNotificationStructure heartbeat = new HeartbeatNotificationStructure();
      heartbeat.setServiceStartedTime(new Date(_serviceStartedTimestamp));
      heartbeat.setStatus(Boolean.TRUE);
      heartbeat.setProducerRef(SiriTypeFactory.particpantRef(_identity));
      heartbeat.setMessageIdentifier(SiriTypeFactory.randomMessageId());
      heartbeat.setRequestTimestamp(new Date());

      Siri siri = new Siri();
      siri.setHeartbeatNotification(heartbeat);

      Object data = siri;

      SiriVersioning instance = SiriVersioning.getInstance();
      data = instance.getPayloadAsVersion(data, _channel.getTargetVersion());

      ServerSubscriptionChannelId channelId = _channel.getId();
      String address = channelId.getAddress();

      try {

        _log.debug("publishing heartbeat to " + address);
        sendHttpRequest(address, siri);

      } catch (SiriConnectionException ex) {
        _log.warn("error publishing heartbeat to " + address, ex);
        // We could be a bit more lenient here?
        terminateChannelSubscription(_channel);
      }
    }
  }

}
