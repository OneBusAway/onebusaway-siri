package org.onebusaway.siri.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.handlers.SiriClientHandler;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.subscriptions.client.SiriClientSubscriptionManager;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriClient extends SiriCommon implements SiriClientHandler,
    SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriClient.class);

  private List<SiriServiceDeliveryHandler> _serviceDeliveryHandlers = new ArrayList<SiriServiceDeliveryHandler>();

  private SiriClientSubscriptionManager _subscriptionManager = new SiriClientSubscriptionManager();

  private boolean _includeDeliveriesToUnknownSubscription = true;

  /**
   * Whether we should wait for a <TerminateSubscriptionResponse/> from a server
   * end-point after sending a <TerminateSubscriptionRequest/> for our active
   * subscriptions on {@link #stop()}.
   */
  private boolean _waitForTerminateSubscriptionResponseOnExit = true;

  private boolean _logRawXml = false;

  public SiriClient() {
    setUrl("http://*:8080/client.xml");
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

  public SiriClientSubscriptionManager getSubscriptionManager() {
    return _subscriptionManager;
  }

  /****
   * Client Start and Stop Methods
   ****/

  /**
   * 
   */
  @Override
  public void start() {

    _log.debug("starting siri client");

    super.start();

    _subscriptionManager.setSiriClientHandler(this);
    _subscriptionManager.start();
  }

  /**
   * 
   */
  @Override
  public void stop() {

    _log.debug("stopping siri client");

    _subscriptionManager.terminateAllSubscriptions(_waitForTerminateSubscriptionResponseOnExit);
    _subscriptionManager.stop();

    super.stop();
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

  /****
   * Private Methods
   ****/

  private void processRequest(SiriClientRequest request) {

    AsynchronousClientConnectionAttempt attempt = new AsynchronousClientConnectionAttempt(
        request);
    _executor.execute(attempt);
  }

  /****
   * 
   ****/

  @Override
  protected void fillSubscriptionRequestStructure(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    super.fillSubscriptionRequestStructure(request, subscriptionRequest);

    _subscriptionManager.registerPendingSubscription(request,
        subscriptionRequest);
  }

  @Override
  protected void handleSiriResponse(Siri siri, boolean asynchronousResponse) {

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

  /****
   *
   ****/

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
