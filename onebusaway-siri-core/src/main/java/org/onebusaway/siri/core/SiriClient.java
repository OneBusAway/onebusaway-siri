package org.onebusaway.siri.core;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriSerializationException;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriClient extends SiriCommon implements SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriClient.class);

  private String _identity;

  protected String _clientUrl;

  protected String _privateClientUrl;

  private List<SiriServiceDeliveryHandler> _serviceDeliveryHandlers = new ArrayList<SiriServiceDeliveryHandler>();

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

  /**
   * 
   * @param serviceRequest
   * @return the immediate service delivery received from the server
   */
  public ServiceDelivery handleServiceRequestWithResponse(
      SiriClientServiceRequest serviceRequest) {

    String targetUrl = serviceRequest.getTargetUrl();
    ServiceRequest request = serviceRequest.getRequest();

    ParticipantRefStructure identity = new ParticipantRefStructure();
    identity.setValue(_identity);
    request.setRequestorRef(identity);

    request.setAddress(_clientUrl);

    MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
    messageIdentifier.setValue(UUID.randomUUID().toString());
    request.setMessageIdentifier(messageIdentifier);

    request.setRequestTimestamp(new Date());

    /**
     * We potentially need to translate the Siri payload to an older version of
     * the specification, as requested by the caller
     */
    SiriVersioning versioning = SiriVersioning.getInstance();

    Object payload = versioning.getPayloadAsVersion(request,
        serviceRequest.getTargetVersion());

    HttpResponse response = sendHttpRequest(targetUrl, payload);

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
  public void handleSubscriptionRequest(
      SiriClientSubscriptionRequest subscriptionRequest) {

    String targetUrl = subscriptionRequest.getTargetUrl();
    SubscriptionRequest request = subscriptionRequest.getRequest();

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

    /**
     * We potentially need to translate the Siri payload to an older version of
     * the specification, as requested by the caller
     */
    SiriVersioning versioning = SiriVersioning.getInstance();

    Object payload = versioning.getPayloadAsVersion(request,
        subscriptionRequest.getTargetVersion());

    ConnectionAttempt attempt = new ConnectionAttempt(targetUrl, payload);
    attempt.setReconnectionInterval(subscriptionRequest.getReconnectionInterval());
    attempt.setRemainingReconnectionAttempts(subscriptionRequest.getReconnectionAttempts());
    _executor.execute(attempt);
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
        for (SiriServiceDeliveryHandler handler : _serviceDeliveryHandlers)
          handler.handleServiceDelivery(delivery);
      }

    } else if (data instanceof ServiceDelivery) {
      ServiceDelivery delivery = (ServiceDelivery) data;
      for (SiriServiceDeliveryHandler handler : _serviceDeliveryHandlers)
        handler.handleServiceDelivery(delivery);
    }
  }

  /****
   * Private Methods
   ****/

  private class ConnectionAttempt implements Runnable {

    private final String targetUrl;
    private final Object payload;
    private int remainingReconnectionAttempts = 0;
    private int reconnectionInterval = 60;

    public ConnectionAttempt(String targetUrl, Object payload) {
      this.targetUrl = targetUrl;
      this.payload = payload;
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
        sendHttpRequest(targetUrl, payload);
      } catch (SiriConnectionException ex) {
        _log.warn("error connecting to " + targetUrl + " ("
            + this.remainingReconnectionAttempts
            + " remaining reconnection attempts)", ex);

        if (this.remainingReconnectionAttempts == 0) {
          return;
        }

        /**
         * We have some reconnection attempts remaining, so we schedule another
         * connection attempt
         */
        if( this.remainingReconnectionAttempts > 0)
          this.remainingReconnectionAttempts--;
        _executor.schedule(this, reconnectionInterval, TimeUnit.SECONDS);
      }
    }
  }
}
