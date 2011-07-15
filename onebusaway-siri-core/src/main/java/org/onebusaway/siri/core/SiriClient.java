package org.onebusaway.siri.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.exceptions.SiriSerializationException;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.subscriptions.SiriClientSubscriptionManager;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractFunctionalServiceRequestStructure;
import uk.org.siri.siri.AbstractRequestStructure;
import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.RequestStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionContextStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;

public class SiriClient extends SiriCommon implements SiriClientHandler,
    SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriClient.class);

  private List<SiriServiceDeliveryHandler> _serviceDeliveryHandlers = new ArrayList<SiriServiceDeliveryHandler>();

  private SiriClientSubscriptionManager _subscriptionManager = new SiriClientSubscriptionManager();

  private ScheduledExecutorService _executor;

  private String _identity;

  private String _clientUrl;

  private String _privateClientUrl;

  /**
   * This is the _clientUrl with hostname wildcard expansion applied. This is
   * what should actually be sent as the consumer address in subscription
   * requests.
   */
  private String _expandedClientUrl;

  private boolean _includeDeliveriesToUnknownSubscription = true;

  /**
   * Whether we should wait for a <TerminateSubscriptionResponse/> from a server
   * end-point after sending a <TerminateSubscriptionRequest/> for our active
   * subscriptions on {@link #stop()}.
   */
  private boolean _waitForTerminateSubscriptionResponseOnExit = true;

  private boolean _logRawXml = false;

  public SiriClient() {
    _identity = UUID.randomUUID().toString();
    setClientUrl("http://*:8080/client.xml");
  }

  public void setIdentity(String identity) {
    _identity = identity;
  }

  /**
   * The public url our client will listen to and expose for url callbacks from
   * the SIRI server. Only used when using publish / subscribe methods. See also
   * {@link #getPrivateClientUrl()}.
   * 
   * @return the client url
   */
  public String getClientUrl() {
    return _clientUrl;
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
   * See the discussion for {@link #setPrivateClientUrl(String)}.
   * 
   * @return the private client url
   */
  public String getPrivateClientUrl() {
    return _privateClientUrl;
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

  /**
   * The internal URL that the client should bind to for incoming pub-sub
   * connection. This defaults to {@link #getClientUrl()}, unless
   * {@link #getPrivateClientUrl()} has been specified.
   * 
   * @param expandHostnameWildcard
   * 
   * @return
   */
  public URL getInternalUrlToBind(boolean expandHostnameWildcard) {

    String clientUrl = _clientUrl;
    if (_privateClientUrl != null)
      clientUrl = _privateClientUrl;

    if (expandHostnameWildcard)
      clientUrl = replaceHostnameWildcardWithPublicHostnameInUrl(clientUrl);

    return url(clientUrl);
  }

  public void addServiceDeliveryHandler(SiriServiceDeliveryHandler handler) {
    _serviceDeliveryHandlers.add(handler);
  }

  public void removeServiceDeliveryHandler(SiriServiceDeliveryHandler handler) {
    _serviceDeliveryHandlers.remove(handler);
  }

  /**
   * By default, we ignore incoming service deliveries if they don't match an
   * existing subscription. If you'd instead like to pass these deliveries
   * onward, set this to true.
   * 
   * @param includeDeliveriesToUnknownSubscription
   */
  public void setIncludeDeliveriesToUnknownSubscription(
      boolean includeDeliveriesToUnknownSubscription) {
    _includeDeliveriesToUnknownSubscription = includeDeliveriesToUnknownSubscription;
  }

  public void setLogRawXml(boolean logRawXml) {
    _logRawXml = logRawXml;
  }
  
  public SiriClientSubscriptionManager getSubscriptionManager() {
    return _subscriptionManager;
  }

  /****
   * Client Start and Stop Methods
   ****/

  /**
   * 
   */
  public void start() {

    _log.debug("starting siri client");

    _subscriptionManager.setSiriClientHandler(this);
    _subscriptionManager.start();

    /**
     * Perform wildcard hostname expansion on our consumer address
     */
    _expandedClientUrl = replaceHostnameWildcardWithPublicHostnameInUrl(_clientUrl);

    /**
     * Check to see if we should be binding to a different internal URL
     */
    checkLocalAddress();

    _executor = Executors.newSingleThreadScheduledExecutor();
  }

  /**
   * 
   */
  public void stop() {

    _log.debug("stopping siri client");

    _subscriptionManager.terminateAllSubscriptions(_waitForTerminateSubscriptionResponseOnExit);

    if (_executor != null)
      _executor.shutdownNow();

    _subscriptionManager.stop();
  }

  /****
   * {@link SiriClientHandler} Interface
   ****/

  @Override
  public Siri handleRequestWithResponse(SiriClientRequest request) {
    return processRequestWithResponse(request);
  }

  @Override
  public void handleRequest(SiriClientRequest request) {
    processRequest(request);
  }

  /*****
   * {@link SiriRawHandler} Interface
   ****/

  @Override
  public void handleRawRequest(Reader reader, Writer writer) {

    if (_logRawXml) {
      try {
        StringBuilder b = new StringBuilder();
        reader = copyReaderToStringBuilder(reader, b);
        _log.info("logging raw xml response:\n=== PUBLISHED BEGIN ===\n"
            + b.toString() + "\n=== PUBLISHED END ===");
      } catch (IOException ex) {
        throw new SiriException("error reading incoming request", ex);
      }
    }

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
      handleSiriResponse(siri, true);
    } else if (data instanceof ServiceDelivery) {
      ServiceDelivery delivery = (ServiceDelivery) data;
      handleServiceDelivery(delivery);
    }
  }

  /****
   * Protected Methods
   ****/

  protected void checkLocalAddress() {

    URL bindUrl = getInternalUrlToBind(false);

    if (!(bindUrl.getHost().equals("*") || bindUrl.getHost().equals("localhost"))) {
      try {
        InetAddress address = InetAddress.getByName(bindUrl.getHost());
        setLocalAddress(address);
      } catch (UnknownHostException ex) {
        _log.warn("error resolving hostname: " + bindUrl.getHost(), ex);
      }
    }
  }

  /****
   * Private Methods
   ****/

  @SuppressWarnings("unchecked")
  private <T> T processRequestWithResponse(SiriClientRequest request) {

    Siri payload = request.getPayload();

    /**
     * We make a deep copy of the SIRI payload so that when we fill in values
     * for the request, the original is unmodified, making it reusable.
     */
    payload = SiriLibrary.copy(payload);

    fillAllSiriRequestStructures(request, payload);

    /**
     * Check to see if we have a subscription request in the payload. If so,
     * register the pending request
     */
    SubscriptionRequest subRequest = payload.getSubscriptionRequest();
    if (subRequest != null) {
      if (!_subscriptionManager.registerPendingSubscription(request, subRequest)) {
        /**
         * If the pending subscription failed to register, we abort
         */
        _log.warn("aborting subscription request (and any other requests contained in the <Siri/> payload");
        return null;
      }
    }

    /**
     * We potentially need to translate the Siri payload to an older version of
     * the specification, as requested by the caller
     */
    SiriVersioning versioning = SiriVersioning.getInstance();
    Object versionedPayload = versioning.getPayloadAsVersion(
        payload, request.getTargetVersion());

    String content = marshallToString(versionedPayload);

    if (_logRawXml)
      _log.info("logging raw xml request:\n=== REQUEST BEGIN ===\n" + content
          + "\n=== REQUEST END ===");

    HttpResponse response = sendHttpRequest(request.getTargetUrl(), content);

    HttpEntity entity = response.getEntity();

    Object responseData = null;
    try {

      Reader responseReader = new InputStreamReader(entity.getContent());

      if (_logRawXml) {
        StringBuilder b = new StringBuilder();
        responseReader = copyReaderToStringBuilder(responseReader, b);
        _log.info("logging raw xml response:\n=== RESPONSE BEGIN ===\n"
            + b.toString() + "\n=== RESPONSE END ===");
      }

      responseData = unmarshall(responseReader);
      responseData = versioning.getPayloadAsVersion(responseData,
          versioning.getDefaultVersion());
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }

    if (responseData instanceof Siri) {
      Siri siri = (Siri) responseData;
      handleSiriResponse(siri, false);
    }

    return (T) responseData;
  }

  private void processRequest(SiriClientRequest request) {

    AsynchronousClientConnectionAttempt attempt = new AsynchronousClientConnectionAttempt(
        request);
    _executor.execute(attempt);
  }

  private void handleSiriResponse(Siri siri, boolean asynchronousResponse) {

    if (siri.getSubscriptionResponse() != null)
      _subscriptionManager.handleSubscriptionResponse(siri.getSubscriptionResponse());

    if (siri.getTerminateSubscriptionResponse() != null)
      _subscriptionManager.handleTerminateSubscriptionResponse(siri.getTerminateSubscriptionResponse());

    if (siri.getCheckStatusResponse() != null)
      _subscriptionManager.handleCheckStatusNotification(siri.getCheckStatusResponse());

    if (siri.getHeartbeatNotification() != null)
      _subscriptionManager.handleHeartbeatNotification(siri.getHeartbeatNotification());

    if (asynchronousResponse) {
      /**
       * We only handle service deliveries if they are asynchronous. If it's a
       * direct response, we assume the client caller will handle the response
       * directly.
       */
      if (siri.getServiceDelivery() != null)
        handleServiceDelivery(siri.getServiceDelivery());
    }
  }

  private void handleServiceDelivery(ServiceDelivery serviceDelivery) {

    checkServiceDeliveryForUnknownSubscriptions(serviceDelivery);

    SiriChannelInfo channelInfo = _subscriptionManager.getChannelInfoForServiceDelivery(serviceDelivery);

    for (SiriServiceDeliveryHandler handler : _serviceDeliveryHandlers)
      handler.handleServiceDelivery(channelInfo, serviceDelivery);
  }

  private void checkServiceDeliveryForUnknownSubscriptions(
      ServiceDelivery serviceDelivery) {

    if (_includeDeliveriesToUnknownSubscription)
      return;

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {

      List<AbstractServiceDeliveryStructure> moduleDeliveries = SiriLibrary.getServiceDeliveriesForModule(
          serviceDelivery, moduleType);

      for (Iterator<AbstractServiceDeliveryStructure> it = moduleDeliveries.iterator(); it.hasNext();) {

        AbstractServiceDeliveryStructure moduleDelivery = it.next();

        if (!_subscriptionManager.isSubscriptionActiveForModuleDelivery(moduleDelivery)) {
          _log.warn("module service delivery of type + " + moduleType
              + " for unknown subcription: TODO");
          it.remove();
        }
      }
    }
  }

  /****
   * 
   ****/

  private void fillAllSiriRequestStructures(SiriClientRequest request, Siri siri) {

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

    fillServiceRequestStructure(siri.getServiceRequest());
    fillSubscriptionRequestStructure(request, siri.getSubscriptionRequest());
  }

  private void fillSubscriptionRequestStructure(SiriClientRequest request,
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

        if (sub.getInitialTerminationTime() == null) {
          Date initialTerminationTime = request.getInitialTerminationTime();
          if (initialTerminationTime == null)
            throw new SiriException(
                "subscription request is missing a required initial termination time");
          sub.setInitialTerminationTime(initialTerminationTime);
        }

        /**
         * TODO: Fill all these in
         */
        if (sub instanceof VehicleMonitoringSubscriptionStructure)
          fillAbstractFunctionalServiceRequestStructure(((VehicleMonitoringSubscriptionStructure) sub).getVehicleMonitoringRequest());
      }
    }
  }

  private void fillRequestStructure(RequestStructure request) {

    if (request == null)
      return;

    request.setRequestorRef(SiriTypeFactory.particpantRef(_identity));

    request.setAddress(_expandedClientUrl);

    if (request.getMessageIdentifier() == null
        || request.getMessageIdentifier().getValue() == null) {
      MessageQualifierStructure messageIdentifier = SiriTypeFactory.randomMessageId();
      request.setMessageIdentifier(messageIdentifier);
    }

    if (request.getRequestTimestamp() == null)
      request.setRequestTimestamp(new Date());
  }

  /**
   * TODO: It sure would be nice if {@link ServiceRequest} was a sub-class of
   * {@link RequestStructure}
   * 
   * @param request
   */
  private void fillServiceRequestStructure(ServiceRequest request) {

    if (request == null)
      return;

    request.setRequestorRef(SiriTypeFactory.particpantRef(_identity));

    request.setAddress(_expandedClientUrl);

    if (request.getRequestTimestamp() == null)
      request.setRequestTimestamp(new Date());

    fillAbstractFunctionalServiceRequestStructures(request.getConnectionMonitoringRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getConnectionTimetableRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getEstimatedTimetableRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getFacilityMonitoringRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getGeneralMessageRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getProductionTimetableRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getSituationExchangeRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getStopMonitoringMultipleRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getStopMonitoringRequest());
    fillAbstractFunctionalServiceRequestStructures(request.getVehicleMonitoringRequest());
  }

  private <T extends AbstractFunctionalServiceRequestStructure> void fillAbstractFunctionalServiceRequestStructures(
      List<T> requests) {
    for (AbstractFunctionalServiceRequestStructure request : requests)
      fillAbstractFunctionalServiceRequestStructure(request);
  }

  private void fillAbstractFunctionalServiceRequestStructure(
      AbstractFunctionalServiceRequestStructure request) {

    fillAbstractRequestStructure(request);
  }

  private void fillAbstractRequestStructure(AbstractRequestStructure request) {
    if (request.getRequestTimestamp() == null)
      request.setRequestTimestamp(new Date());
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

    private final SiriClientRequest request;

    private int remainingReconnectionAttempts = 0;
    private int reconnectionInterval = 60;
    private int connectionErrorCount = 0;

    public AsynchronousClientConnectionAttempt(SiriClientRequest request) {
      this.request = request;
      this.remainingReconnectionAttempts = request.getReconnectionAttempts();
      this.reconnectionInterval = request.getReconnectionInterval();
    }

    @Override
    public void run() {

      try {
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
           * We display the full exception on the first connection error, but
           * hide it on recurring errors
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
           * We have some reconnection attempts remaining, so we schedule
           * another connection attempt
           */
          if (this.remainingReconnectionAttempts > 0)
            this.remainingReconnectionAttempts--;

          _executor.schedule(this, reconnectionInterval, TimeUnit.SECONDS);
        }

      } catch (Throwable ex) {
        _log.error("error executing asynchronous client request", ex);
      }
    }
  }

}
