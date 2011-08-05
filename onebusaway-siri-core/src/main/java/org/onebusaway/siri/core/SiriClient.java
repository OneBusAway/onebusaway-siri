package org.onebusaway.siri.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

  /**
   * See {@link SiriClientHandler#handleRequestWithResponse(SiriClientRequest)}.
   */
  @Override
  public Siri handleRequestWithResponse(SiriClientRequest request) {
    request.resetConnectionStatistics();
    return processRequestWithResponse(request);
  }

  /**
   * See {@link SiriClientHandler#handleRequest(SiriClientRequest)}.
   */
  @Override
  public void handleRequest(SiriClientRequest request) {
    processRequestWithAsynchronousResponse(request);
  }

  /*****
   * {@link SiriRawHandler} Interface
   ****/

  @Override
  public void handleRawRequest(Reader reader, Writer writer) {

    String responseContent = null;

    if (_logRawXmlType != ELogRawXmlType.NONE) {
      try {
        StringBuilder b = new StringBuilder();
        reader = copyReaderToStringBuilder(reader, b);
        responseContent = b.toString();
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
      if (isRawDataLogged(siri)) {
        _log.info("logging raw xml response:\n=== PUBLISHED BEGIN ===\n"
            + responseContent + "\n=== PUBLISHED END ===");
      }
      handleSiriResponse(siri, true);
    }
  }

  /****
   * Protected Methods
   ****/

  /**
   * We override the common method to add custom subscription-management
   * behavior
   */
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
   * Private Methods
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
}
