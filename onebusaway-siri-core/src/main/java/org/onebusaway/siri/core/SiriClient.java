/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2011 Google, Inc.
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
package org.onebusaway.siri.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.guice.LifecycleService;
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

/**
 * A SIRI client implementation. Typically, you don't instantiate this directly,
 * but instead let the Guice framework do the configuration. See
 * {@link SiriCoreModule} for more details.
 * 
 * Here is a quick example:
 * 
 * <pre>
 *   // Configure the Guice container
 *   List<Module> modules = new ArrayList<Module>();
 *   modules.addAll(SiriCoreModule.getModules());
 *   modules.add(new SiriJettyModule());
 *   Injector injector = Guice.createInjector(modules);
 *
 *   SiriClient client = injector.getInstance(SiriClient.class);
 *   // Set our SIRI identity
 *   client.setIdentify("me");
 *   // Change the port and url we listen to for incoming service deliveries
 *   client.setUrl("http://*:8080/client.xml");
 *   // Register a service delivery handler
 *   client.addServiceDeliveryHandler(...);
 *   
 *   // Start the client
 *   LifecycleService lifecycleService = injector.getInstance(LifecycleService.class);
 *   lifecycleService.start();
 *   
 *   // Send a request
 *   SiriClient request = ...
 *   client.handleRequest(request);
 * </pre>
 * 
 * @author bdferris
 * @see SiriServer
 * @see SiriClientRequest
 * @see SiriClientRequestFactory
 */
@Singleton
public class SiriClient extends SiriCommon implements SiriClientHandler,
    SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriClient.class);

  private List<SiriServiceDeliveryHandler> _serviceDeliveryHandlers = new ArrayList<SiriServiceDeliveryHandler>();

  private SiriClientSubscriptionManager _subscriptionManager;

  private boolean _includeDeliveriesToUnknownSubscription = true;

  /**
   * Whether we should wait for a <TerminateSubscriptionResponse/> from a server
   * end-point after sending a <TerminateSubscriptionRequest/> for our active
   * subscriptions on {@link #stop()}.
   */
  private boolean _waitForTerminateSubscriptionResponseOnExit = true;

  private AtomicInteger _serviceDeliveryCounter = new AtomicInteger();

  public SiriClient() {
    setUrl("http://*:8080/client.xml");
  }

  @Inject
  public void setSubscriptionManager(
      SiriClientSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  /**
   * Add a service delivery handler to receive notification of
   * {@link ServiceDelivery} deliveries from a remote SIRI endpoint.
   * 
   * @param handler the service delivery handler
   */
  public void addServiceDeliveryHandler(SiriServiceDeliveryHandler handler) {
    _serviceDeliveryHandlers.add(handler);
  }

  /**
   * Remove a previously registered service delivery handler
   * 
   * @param handler the handler to remove
   */
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

  /**
   * Call when ready to stop the client. The method will automatically terminate
   * any open subscriptions. Note that this method is typically called
   * automatically by the {@link LifecycleService}.
   */
  @PreDestroy
  public void stop() {
    _subscriptionManager.terminateAllSubscriptions(_waitForTerminateSubscriptionResponseOnExit);
  }

  /****
   * {@link SiriClientHandler} Interface
   ****/

  /**
   * See {@link SiriClientHandler#handleRequestWithResponse(SiriClientRequest)}.
   */
  @Override
  public Siri handleRequestWithResponse(SiriClientRequest request) {
    checkRequest(request);
    request.resetConnectionStatistics();
    return processRequestWithResponse(request);
  }

  /**
   * See {@link SiriClientHandler#handleRequest(SiriClientRequest)}.
   */
  @Override
  public void handleRequest(SiriClientRequest request) {
    checkRequest(request);
    request.resetConnectionStatistics();
    processRequestWithAsynchronousResponse(request);
  }

  /**
   * See
   * {@link SiriClientHandler#handleRequestReconnectIfApplicable(SiriClientRequest)}
   */
  @Override
  public void handleRequestReconnectIfApplicable(SiriClientRequest request) {
    checkRequest(request);
    /**
     * Note that we DON'T reset connection statistics on the request, because
     * this is a reconnect, as opposed to an initial attempt
     */
    reattemptRequestIfApplicable(request);
  }

  /*****
   * {@link SiriRawHandler} Interface
   ****/

  /**
   * See {@link SiriRawHandler#handleRawRequest(Reader, Writer)}.
   */
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
   * Called to verify that a {@link SiriClientRequest} is not missing any
   * necessary fields.
   */
  protected void checkRequest(SiriClientRequest request) {
    if (request == null)
      throw new IllegalArgumentException("request is null");
    if (request.getTargetUrl() == null)
      throw new IllegalArgumentException("targetUrl is null for request");
    if (request.getTargetVersion() == null)
      throw new IllegalArgumentException("targetVersion is null for request");
    if (request.getPayload() == null)
      throw new IllegalArgumentException("payload is null for request");
  }

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

  /**
   * Check to see if there is a pending subscription that can be cleared on a
   * failed request
   */
  @Override
  protected void cleanupFailedRequest(SiriClientRequest request,
      Siri failedPayload) {
    super.cleanupFailedRequest(request, failedPayload);

    /**
     * If we are reattempting a subscription request, we need to make sure to
     * clean up an existing request data
     */
    if (failedPayload.getSubscriptionRequest() != null)
      _subscriptionManager.clearPendingSubscription(request,
          failedPayload.getSubscriptionRequest());
  }

  /**
   * Handle an incoming response from a SIRI endpoint. Checks if any
   * subscription-related responses are contained in the response and, if so,
   * takes appropriate action.
   */
  @Override
  protected void handleSiriResponse(Siri siri, boolean asynchronousResponse) {

    super.handleSiriResponse(siri, asynchronousResponse);

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
   * {@link StatusProviderService} Interface
   ****/

  @Override
  public void getStatus(Map<String, String> status) {
    super.getStatus(status);
    status.put("siri.client.serviceDeliveryCounter",
        Integer.toString(_serviceDeliveryCounter.get()));
  }

  /****
   * Private Methods
   ****/

  private void handleServiceDelivery(ServiceDelivery serviceDelivery) {

    _serviceDeliveryCounter.incrementAndGet();

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
