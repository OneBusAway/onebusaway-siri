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

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.guice.LifecycleService;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.handlers.SiriRequestResponseHandler;
import org.onebusaway.siri.core.handlers.SiriSubscriptionRequestHandler;
import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionEvent;
import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionManager;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.MessageRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

/**
 * A SIRI server implementation. Typically, you don't instantiate this directly,
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
 *   SiriServer server = injector.getInstance(SiriServer.class);
 *   // Set our SIRI identity
 *   server.setIdentify("me");
 *   // Change the port and url we listen to for incoming client requests
 *   server.setUrl("http://*:8080/server.xml");
 *   
 *   // Start the client
 *   LifecycleService lifecycleService = injector.getInstance(LifecycleService.class);
 *   lifecycleService.start();
 *   
 *   // Publish a ServiceDelivery
 *   ServiceDelivery delivery = ...
 *   server.puslish(delivery);
 * </pre>
 * 
 * @author bdferris
 * @see SiriClient
 * @see ServiceDelivery
 */
@Singleton
public class SiriServer extends SiriCommon implements SiriRawHandler {

  private static Logger _log = LoggerFactory.getLogger(SiriServer.class);

  private SiriServerSubscriptionManager _subscriptionManager = new SiriServerSubscriptionManager();

  private List<SiriRequestResponseHandler> _requestResponseHandlers = new ArrayList<SiriRequestResponseHandler>();

  private List<SiriSubscriptionRequestHandler> _subscriptionRequestHandlers = new ArrayList<SiriSubscriptionRequestHandler>();

  private long _serviceStartedTimestamp;

  public SiriServer() {
    setUrl("http://*:8080/server.xml");
  }

  @Inject
  public void setSubscriptionManager(
      SiriServerSubscriptionManager subscriptionManager) {
    _subscriptionManager = subscriptionManager;
  }

  /**
   * 
   * @return the time at which the server started, in ms since the epoch
   */
  public long getServiceStartedTimestamp() {
    return _serviceStartedTimestamp;
  }

  /**
   * Add a request-response handler if you want to respond directly to a
   * {@link ServiceRequest} from a client.
   * 
   * @param handler the request-response handler
   */
  public void addRequestResponseHandler(SiriRequestResponseHandler handler) {
    _requestResponseHandlers.add(handler);
  }

  /**
   * Remove an existing request-response handler
   * 
   * @param handler the handler to remove
   */
  public void removeRequestResponseHandler(SiriRequestResponseHandler handler) {
    _requestResponseHandlers.remove(handler);
  }

  /**
   * Add a handler to receive notification every time a subscription request is
   * received from a client.
   * 
   * @param handler the subscription request handler
   */
  public void addSubscriptionRequestHandler(
      SiriSubscriptionRequestHandler handler) {
    _subscriptionRequestHandlers.add(handler);
  }

  /**
   * Remove an existing subscription request handler.
   * 
   * @param handler the handler to remove
   */
  public void removeSubscriptionRequestHandler(
      SiriSubscriptionRequestHandler handler) {
    _subscriptionRequestHandlers.remove(handler);
  }

  /****
   * 
   ****/

  /**
   * This method is called on server startup. Typically, calling this method is
   * handled automatically by the {@link LifecycleService}.
   */
  @PostConstruct
  public void start() {
    _serviceStartedTimestamp = System.currentTimeMillis();
  }

  /****
   * Server Methods
   ***/

  /**
   * Publish a {@link ServiceDelivery} to any connected clients based on the
   * contents of the delivery.
   * 
   * @param serviceDelivery the delivery to publish
   * @return the number of clients the delivery is published to
   */
  public int publish(ServiceDelivery serviceDelivery) {

    List<SiriServerSubscriptionEvent> events = _subscriptionManager.publish(serviceDelivery);

    _log.debug("server subscription events: {}", events.size());

    if (!events.isEmpty()) {

      if (_log.isDebugEnabled())
        _log.debug("SiriPublishEvents=" + events.size());

      for (SiriServerSubscriptionEvent event : events)
        _schedulingService.submit(new PublishEventTask(event));
    }

    return events.size();
  }

  /****
   * {@link SiriRawHandler} Interface
   ****/

  /**
   * See {@link SiriRawHandler#handleRawRequest(Reader, Writer)}.
   */
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

    if (!(data instanceof Siri))
      throw new SiriException("expected a " + Siri.class
          + " payload but instead received " + data.getClass());

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

    fillAllSiriStructures(siriResponse);

    /**
     * Send the (properly versioned) response
     */
    Object responseData = versioning.getPayloadAsVersion(siriResponse,
        originalVersion);
    marshall(responseData, writer);
  }

  /****
   * Private Methods
   ****/

  private SubscriptionResponseStructure handleSubscriptionRequest(
      SubscriptionRequest subscriptionRequest, ESiriVersion originalVersion) {

    _log.debug("handling SubscriptionRequest");

    for (SiriSubscriptionRequestHandler handler : _subscriptionRequestHandlers)
      handler.handleSubscriptionRequest(subscriptionRequest);

    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    response.setServiceStartedTime(new Date(_serviceStartedTimestamp));
    response.setRequestMessageRef(subscriptionRequest.getMessageIdentifier());

    List<StatusResponseStructure> statuses = response.getResponseStatus();

    _subscriptionManager.handleSubscriptionRequest(subscriptionRequest,
        originalVersion, statuses);

    return response;
  }

  private ServiceDelivery handleServiceRequest(ServiceRequest serviceRequest) {
    ServiceDelivery response = new ServiceDelivery();

    for (SiriRequestResponseHandler handler : _requestResponseHandlers)
      handler.handleRequestAndResponse(serviceRequest, response);

    return response;
  }

  private CheckStatusResponseStructure handleCheckStatusRequest(
      CheckStatusRequestStructure request) {

    CheckStatusResponseStructure response = new CheckStatusResponseStructure();
    response.setStatus(Boolean.TRUE);

    MessageQualifierStructure messageId = request.getMessageIdentifier();
    if (messageId != null) {
      MessageRefStructure ref = new MessageRefStructure();
      ref.setValue(messageId.getValue());
      response.setRequestMessageRef(ref);
    }

    response.setServiceStartedTime(new Date(_serviceStartedTimestamp));

    return response;
  }

  private TerminateSubscriptionResponseStructure handleTerminateSubscriptionRequest(
      TerminateSubscriptionRequestStructure request) {

    TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
    response.setRequestMessageRef(request.getMessageIdentifier());

    List<TerminationResponseStatus> statuses = response.getTerminationResponseStatus();

    _subscriptionManager.terminateSubscriptionsForRequest(request, statuses);

    return response;
  }

  /****
   * 
   ****/

  private void publishResponse(SiriServerSubscriptionEvent event) {

    String address = event.getAddress();
    ESiriVersion targetVersion = event.getTargetVersion();
    ServiceDelivery delivery = event.getDelivery();

    Siri siri = new Siri();
    siri.setServiceDelivery(delivery);

    fillAllSiriStructures(siri);

    /**
     * Make sure the outgoing SIRI data is updated to the client version
     */
    SiriVersioning versioning = SiriVersioning.getInstance();
    Object data = versioning.getPayloadAsVersion(siri, targetVersion);

    try {
      String content = marshallToString(data);
      sendHttpRequest(address, content);
    } catch (SiriConnectionException ex) {
      _log.warn("error connecting to client at " + address);
      _subscriptionManager.terminateSubscriptionWithId(event.getSubscriptionId());
    }
  }

  /****
   * 
   ****/

  private class PublishEventTask implements Runnable {

    private final SiriServerSubscriptionEvent _event;

    public PublishEventTask(SiriServerSubscriptionEvent event) {
      _event = event;
    }

    @Override
    public void run() {
      try {
        publishResponse(_event);
      } catch (Throwable ex) {
        _log.warn("error publishing to " + _event.getSubscriptionId(), ex);
        _subscriptionManager.terminateSubscriptionWithId(_event.getSubscriptionId());
      }
    }
  }
}
