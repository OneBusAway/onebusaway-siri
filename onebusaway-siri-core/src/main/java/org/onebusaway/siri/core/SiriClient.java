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

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriSerializationException;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.HeartbeatNotificationStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.RequestStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionContextStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;

public class SiriClient extends SiriCommon implements SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriClient.class);

  private String _identity;

  protected String _clientUrl;

  protected String _privateClientUrl;

  private List<SiriServiceDeliveryHandler> _serviceDeliveryHandlers = new ArrayList<SiriServiceDeliveryHandler>();

  private ConcurrentMap<String, ClientSubscriptionChannel> _channelsById = new ConcurrentHashMap<String, ClientSubscriptionChannel>();

  private ScheduledExecutorService _executor;

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
    _executor = Executors.newSingleThreadScheduledExecutor();
  }

  public void stop() {
    if (_executor != null)
      _executor.shutdownNow();
  }

  /****
   * Primary Client Methods
   ****/

  public Siri handleSiriRequestWithResponse(SiriClientRequest request) {

    Siri siri = request.getPayload();
    fillAllSiriRequestStructures(siri);
    return processRequestWithResponse(request);
  }

  public void handleSiriRequest(SiriClientRequest request) {

    Siri siri = request.getPayload();
    fillAllSiriRequestStructures(siri);
    processRequest(request);
  }

  /**
   * 
   * @param request
   * @return the immediate service delivery received from the server
   */
  public ServiceDelivery handleServiceRequestWithResponse(
      SiriClientServiceRequest request) {

    fillRequestStructure(request.getPayload());

    return processRequestWithResponse(request);
  }

  /**
   * 
   * @param request
   */
  public void handleServiceRequest(SiriClientServiceRequest request) {

    fillRequestStructure(request.getPayload());
    processRequest(request);
  }

  /**
   * 
   * @param request the subscription request
   */
  public Siri handleSubscriptionRequestWithResponse(
      SiriClientSubscriptionRequest request) {

    SubscriptionRequest subRequest = request.getPayload();

    fillRequestStructure(subRequest);
    fillSubscriptionRequestStructure(request, subRequest);

    return processRequestWithResponse(request);
  }

  /**
   * 
   * @param request the subscription request
   */
  public void handleSubscriptionRequest(SiriClientSubscriptionRequest request) {

    SubscriptionRequest subRequest = request.getPayload();

    fillRequestStructure(subRequest);
    fillSubscriptionRequestStructure(request, subRequest);

    processRequest(request);
  }

  /*****
   * {@link SiriRawHandler} Interface
   ****/

  @Override
  public void handleRawRequest(Reader reader, Writer writer) {

    Object data = unmarshall(reader);

    /**
     * We potentially need to translate the Siri payload from an older version
     * of the specification. We always operate on objects from the newest
     * version of the spec
     */
    SiriVersioning instance = SiriVersioning.getInstance();
    data = instance.getPayloadAsVersion(data, instance.getDefaultVersion());

    if (data instanceof Siri) {
      Siri siri = (Siri) data;

      ServiceDelivery delivery = siri.getServiceDelivery();
      if (delivery != null) {
        handleServiceDelivery(delivery);
      }

      HeartbeatNotificationStructure heartbeat = siri.getHeartbeatNotification();
      if (heartbeat != null) {
        handleHeartbeatNotification(heartbeat);
      }

    } else if (data instanceof ServiceDelivery) {
      ServiceDelivery delivery = (ServiceDelivery) data;
      handleServiceDelivery(delivery);
    }
  }

  /****
   * Private Methods
   ****/

  @SuppressWarnings("unchecked")
  private <T> T processRequestWithResponse(AbstractSiriClientRequest<?> request) {

    String targetUrl = request.getTargetUrl();

    /**
     * We potentially need to translate the Siri payload to an older version of
     * the specification, as requested by the caller
     */
    SiriVersioning versioning = SiriVersioning.getInstance();

    Object payload = versioning.getPayloadAsVersion(request.getPayload(),
        request.getTargetVersion());

    HttpResponse response = sendHttpRequest(targetUrl, payload);

    HttpEntity entity = response.getEntity();

    Object responseData = null;
    try {
      responseData = unmarshall(entity.getContent());
      responseData = versioning.getPayloadAsVersion(responseData,
          versioning.getDefaultVersion());
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }

    SubscriptionRequest subRequest = null;
    SubscriptionResponseStructure subResponse = null;

    if (request instanceof SiriClientRequest) {
      SiriClientRequest r = (SiriClientRequest) request;
      Siri siri = r.getPayload();
      subRequest = siri.getSubscriptionRequest();
    } else if (request instanceof SiriClientSubscriptionRequest) {
      SiriClientSubscriptionRequest r = (SiriClientSubscriptionRequest) request;
      subRequest = r.getPayload();
    }

    if (responseData instanceof Siri) {
      Siri siri = (Siri) responseData;
      subResponse = siri.getSubscriptionResponse();
    }

    if (subRequest != null && subResponse != null)
      registerSubscription(request, subRequest, subResponse);

    return (T) responseData;
  }

  private void processRequest(AbstractSiriClientRequest<?> request) {

    AsynchronousClientConnectionAttempt attempt = new AsynchronousClientConnectionAttempt(
        request);
    attempt.setReconnectionInterval(request.getReconnectionInterval());
    attempt.setRemainingReconnectionAttempts(request.getReconnectionAttempts());

    _executor.execute(attempt);
  }

  private void registerSubscription(AbstractSiriClientRequest<?> request,
      SubscriptionRequest subscriptionRequest,
      SubscriptionResponseStructure subResponse) {

    String address = request.getTargetUrl();
    String serverId = address;

    ParticipantRefStructure responderRef = subResponse.getResponderRef();
    if (responderRef != null && responderRef.getValue() != null)
      serverId = responderRef.getValue();

    ESiriVersion targetVersion = request.getTargetVersion();

    ClientSubscriptionChannel channel = getChannelForServer(serverId, address,
        targetVersion);

    Date serviceStartedTime = subResponse.getServiceStartedTime();
    if (serviceStartedTime != null)
      channel.setLastServiceStartedTime(serviceStartedTime);

    ConcurrentMap<String, ClientSubscriptionInstance> subscriptions = channel.getSubscriptions();

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {

      List<AbstractSubscriptionStructure> requests = SiriLibrary.getSubscriptionRequestsForModule(
          subscriptionRequest, moduleType);

      for (AbstractSubscriptionStructure subRequest : requests) {

        SubscriptionQualifierStructure subId = subRequest.getSubscriptionIdentifier();
        String subscriptionId = subId.getValue();

        ClientSubscriptionInstance instance = new ClientSubscriptionInstance(
            channel, subscriptionId, moduleType, subRequest);

        ClientSubscriptionInstance existing = subscriptions.put(subscriptionId,
            instance);

        if (existing != null) {
          _log.warn("existing subscription");
        }
      }
    }

    synchronized (channel) {

      channel.setReconnectionAttempts(request.getReconnectionAttempts());
      channel.setReconnectionInterval(request.getReconnectionInterval());

      long heartbeatInterval = request.getHeartbeatInterval();

      if (heartbeatInterval != channel.getHeartbeatInterval()) {
        channel.setHeartbeatInterval(heartbeatInterval);

        resetHeartbeat(channel, heartbeatInterval);
      }

      long checkStatusInterval = request.getCheckStatusInterval();

      if (checkStatusInterval > 0) {
        channel.setCheckStatusInterval(checkStatusInterval);

        resetCheckStatusTask(channel, checkStatusInterval);
      }
    }
  }

  private void resetHeartbeat(ClientSubscriptionChannel channel,
      long heartbeatInterval) {

    ScheduledFuture<?> heartbeatTask = channel.getHeartbeatTask();
    if (heartbeatTask != null)
      heartbeatTask.cancel(true);

    if (heartbeatInterval > 0) {
      ClientHeartbeatTimeoutTask task = new ClientHeartbeatTimeoutTask(channel);

      // Why is this * 2? We want to give the heartbeat a little slack time.
      // Could be better...
      heartbeatTask = _executor.schedule(task, heartbeatInterval * 2,
          TimeUnit.SECONDS);
      channel.setHeartbeatTask(heartbeatTask);
    }
  }

  private void resetCheckStatusTask(ClientSubscriptionChannel channel,
      long checkStatusInterval) {

    ScheduledFuture<?> checkStatusTask = channel.getCheckStatusTask();
    if (checkStatusTask != null) {
      checkStatusTask.cancel(true);
      channel.setCheckStatusTask(null);
    }

    if (checkStatusInterval > 0) {
      ClientCheckStatusTask task = new ClientCheckStatusTask(channel);
      checkStatusTask = _executor.scheduleAtFixedRate(task,
          checkStatusInterval, checkStatusInterval, TimeUnit.SECONDS);
      channel.setCheckStatusTask(checkStatusTask);
    }
  }

  private ClientSubscriptionChannel getChannelForServer(String serverId,
      String address, ESiriVersion targetVersion) {

    ClientSubscriptionChannel channel = _channelsById.get(serverId);

    if (channel == null) {

      ClientSubscriptionChannel newChannel = new ClientSubscriptionChannel(
          serverId, address, targetVersion);

      channel = _channelsById.put(serverId, newChannel);
      if (channel == null)
        channel = newChannel;
    }
    return channel;
  }

  private void handleServiceDelivery(ServiceDelivery serviceDelivery) {

    for (SiriServiceDeliveryHandler handler : _serviceDeliveryHandlers)
      handler.handleServiceDelivery(serviceDelivery);
  }

  private void handleHeartbeatNotification(
      HeartbeatNotificationStructure heartbeat) {

    _log.debug("hearbeat notification");

    ClientSubscriptionChannel channel = null;

    ParticipantRefStructure producerRef = heartbeat.getProducerRef();
    if (producerRef != null && producerRef.getValue() != null) {
      channel = _channelsById.get(producerRef.getValue());
    }

    if (channel == null && heartbeat.getAddress() != null)
      channel = _channelsById.get(heartbeat.getAddress());

    if (channel != null) {
      synchronized (channel) {
        resetHeartbeat(channel, channel.getHeartbeatInterval());
      }
    }
  }

  /****
   * 
   ****/

  private void fillAllSiriRequestStructures(Siri siri) {

    fillRequestStructure(siri.getCapabilitiesRequest());
    fillRequestStructure(siri.getCheckStatusRequest());
    fillRequestStructure(siri.getFacilityRequest());
    fillRequestStructure(siri.getInfoChannelRequest());
    fillRequestStructure(siri.getLinesRequest());
    fillRequestStructure(siri.getProductCategoriesRequest());
    fillRequestStructure(siri.getServiceFeaturesRequest());
    fillRequestStructure(siri.getStopPointsRequest());
    fillRequestStructure(siri.getSubscriptionRequest());
    fillRequestStructure(siri.getTerminateSubscriptionRequest());
    fillRequestStructure(siri.getVehicleFeaturesRequest());

    fillRequestStructure(siri.getServiceRequest());
  }

  private void fillSubscriptionRequestStructure(
      AbstractSiriClientRequest<?> request,
      SubscriptionRequest subscriptionRequest) {

    if (subscriptionRequest == null)
      return;

    int heartbeatInterval = request.getHeartbeatInterval();
    if (heartbeatInterval > 0) {
      DatatypeFactory dataTypeFactory = createDataTypeFactory();
      Duration interval = dataTypeFactory.newDuration(heartbeatInterval * 1000);
      SubscriptionContextStructure context = new SubscriptionContextStructure();
      context.setHeartbeatInterval(interval);
      subscriptionRequest.setSubscriptionContext(context);
    }

    /**
     * Fill in subscription ids
     */
    for (ESiriModuleType moduleType : ESiriModuleType.values()) {

      List<AbstractSubscriptionStructure> subs = SiriLibrary.getSubscriptionRequestsForModule(
          subscriptionRequest, moduleType);

      for (AbstractSubscriptionStructure sub : subs) {

        if (sub.getSubscriberRef() == null)
          sub.setSubscriberRef(SiriTypeFactory.particpantRef(_identity));

        if (sub.getSubscriptionIdentifier() == null)
          sub.setSubscriptionIdentifier(SiriTypeFactory.randomSubscriptionId());
      }
    }
  }

  private void fillRequestStructure(RequestStructure request) {

    if (request == null)
      return;

    request.setRequestorRef(SiriTypeFactory.particpantRef(_identity));

    request.setAddress(_clientUrl);

    MessageQualifierStructure messageIdentifier = SiriTypeFactory.randomMessageId();
    request.setMessageIdentifier(messageIdentifier);

    request.setRequestTimestamp(new Date());
  }

  /**
   * TODO: It sure would be nice if {@link ServiceRequest} was a sub-class of
   * {@link RequestStructure}
   * 
   * @param request
   */
  private void fillRequestStructure(ServiceRequest request) {

    if( request == null)
      return;
    
    request.setRequestorRef(SiriTypeFactory.particpantRef(_identity));

    request.setAddress(_clientUrl);

    MessageQualifierStructure messageIdentifier = SiriTypeFactory.randomMessageId();
    request.setMessageIdentifier(messageIdentifier);

    request.setRequestTimestamp(new Date());
  }

  private void handleDisconnectAndReconnect(ClientSubscriptionChannel channel) {

    _log.info("terminate subscription: {}", channel);
    _channelsById.remove(channel.getServerId());

    synchronized (channel) {
      resetHeartbeat(channel, 0);
      resetCheckStatusTask(channel, 0);
    }

    SubscriptionRequest request = new SubscriptionRequest();

    ConcurrentMap<String, ClientSubscriptionInstance> subscriptions = channel.getSubscriptions();

    for (ClientSubscriptionInstance instance : subscriptions.values()) {
      ESiriModuleType moduleType = instance.getModuleType();
      AbstractSubscriptionStructure subRequest = instance.getSubscriptionRequest();
      subRequest.setSubscriptionIdentifier(null);
      List<AbstractSubscriptionStructure> subRequests = SiriLibrary.getSubscriptionRequestsForModule(
          request, moduleType);
      subRequests.add(subRequest);
    }

    SiriClientSubscriptionRequest clientRequest = new SiriClientSubscriptionRequest();
    clientRequest.setCheckStatusInterval((int) channel.getCheckStatusInterval());
    clientRequest.setHeartbeatInterval((int) channel.getHeartbeatInterval());
    clientRequest.setReconnectionInterval(channel.getReconnectionInterval());
    clientRequest.setReconnectionAttempts(channel.getReconnectionAttempts());
    clientRequest.setTargetUrl(channel.getAddress());
    clientRequest.setTargetVersion(channel.getTargetVersion());
    clientRequest.setPayload(request);

    handleSubscriptionRequest(clientRequest);
  }

  private void checkStatus(ClientSubscriptionChannel channel) {

    CheckStatusRequestStructure checkStatus = new CheckStatusRequestStructure();
    checkStatus.setRequestTimestamp(new Date());

    Siri siri = new Siri();
    siri.setCheckStatusRequest(checkStatus);

    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl(channel.getAddress());
    request.setTargetVersion(channel.getTargetVersion());
    request.setPayload(siri);

    Siri siriResponse = null;

    try {
      siriResponse = handleSiriRequestWithResponse(request);
    } catch (Throwable ex) {
      _log.warn("error performing check-status on channel=" + channel, ex);
      siriResponse = null;
    }

    if (!isCheckStatusValid(channel, siriResponse)) {

      /**
       * The check status did not succeed, so we cancel the subscription
       */
      _log.warn("check status failed");
      handleDisconnectAndReconnect(channel);
    }

  }

  private boolean isCheckStatusValid(ClientSubscriptionChannel channel,
      Siri response) {

    if (response == null)
      return false;

    CheckStatusResponseStructure checkStatusResponse = response.getCheckStatusResponse();

    if (checkStatusResponse == null)
      return true;

    Date serviceStartedTime = checkStatusResponse.getServiceStartedTime();

    /**
     * Has the service start time been adjusted since our last status check?
     */
    if (serviceStartedTime != null) {
      Date lastServiceStartedTime = channel.getLastServiceStartedTime();
      if (lastServiceStartedTime == null) {
        channel.setLastServiceStartedTime(serviceStartedTime);
      } else if (serviceStartedTime.after(lastServiceStartedTime)) {
        channel.setLastServiceStartedTime(serviceStartedTime);
        return false;
      }
    }

    return true;
  }

  private DatatypeFactory createDataTypeFactory() {
    try {
      return DatatypeFactory.newInstance();
    } catch (DatatypeConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  /****
   * 
   ****/

  /**
   * A runnable task that attempts a connection from a SIRI client to a SIRI
   * server. Handles reconnection semantics.
   * 
   * @author bdferris
   */
  private class AsynchronousClientConnectionAttempt implements Runnable {

    private final AbstractSiriClientRequest<?> request;

    private int remainingReconnectionAttempts = 0;
    private int reconnectionInterval = 60;
    private int connectionErrorCount = 0;

    public AsynchronousClientConnectionAttempt(
        AbstractSiriClientRequest<?> request) {
      this.request = request;
    }

    public void setRemainingReconnectionAttempts(
        int remainingReconnectionAttempts) {
      this.remainingReconnectionAttempts = remainingReconnectionAttempts;
    }

    public void setReconnectionInterval(int reconnectionInterval) {
      this.reconnectionInterval = reconnectionInterval;
    }

    @Override
    public void run() {

      try {

        processRequestWithResponse(request);

        /**
         * Reset our connection error count and note that we've successfully
         * reconnected if the we've had problems before
         */
        if (connectionErrorCount > 0)
          _log.info("successfully reconnected to " + request.getTargetUrl());
        connectionErrorCount = 0;

      } catch (SiriConnectionException ex) {

        String message = "error connecting to " + request.getTargetUrl()
            + " (remainingConnectionAttempts="
            + this.remainingReconnectionAttempts + " connectionErrorCount="
            + connectionErrorCount + ")";

        /**
         * We display the full exception on the first connection error, but hide
         * it on recurring errors
         */
        if (connectionErrorCount == 0) {
          _log.warn(message, ex);
        } else {
          _log.warn(message);
        }

        connectionErrorCount++;

        if (this.remainingReconnectionAttempts == 0) {
          return;
        }

        /**
         * We have some reconnection attempts remaining, so we schedule another
         * connection attempt
         */
        if (this.remainingReconnectionAttempts > 0)
          this.remainingReconnectionAttempts--;

        _executor.schedule(this, reconnectionInterval, TimeUnit.SECONDS);
      }
    }
  }

  /**
   * This task is run when a {@link SiriClient} heartbeat times out, canceling
   * the active subscription for that connection.
   * 
   * @author bdferris
   */
  private class ClientHeartbeatTimeoutTask implements Runnable {

    private final ClientSubscriptionChannel channel;

    public ClientHeartbeatTimeoutTask(ClientSubscriptionChannel channel) {
      this.channel = channel;
    }

    @Override
    public void run() {
      _log.warn("heartbeat interval timeout: " + channel.getAddress());
      handleDisconnectAndReconnect(channel);
    }
  }

  private class ClientCheckStatusTask implements Runnable {

    private final ClientSubscriptionChannel channel;

    public ClientCheckStatusTask(ClientSubscriptionChannel channel) {
      this.channel = channel;
    }

    @Override
    public void run() {
      try {
        checkStatus(channel);
      } catch (Throwable ex) {
        _log.warn("unexpected error while performing check-status for channel="
            + channel, ex);
      }
    }
  }
}
