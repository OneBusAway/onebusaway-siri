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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.exceptions.SiriSerializationException;
import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.onebusaway.siri.core.services.HttpClientService;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.services.StatusProviderService;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractFunctionalServiceRequestStructure;
import uk.org.siri.siri.AbstractRequestStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ProducerResponseStructure;
import uk.org.siri.siri.RequestStructure;
import uk.org.siri.siri.ResponseEndpointStructure;
import uk.org.siri.siri.ResponseStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionContextStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;

/**
 * Common base configuration and methods used by both {@link SiriClient} and
 * {@link SiriServer}.
 * 
 * @author bdferris
 * 
 */
public class SiriCommon implements SiriRawHandler, StatusProviderService {

  public enum ELogRawXmlType {
    NONE, CONTROL, DATA, ALL
  }

  private static Logger _log = LoggerFactory.getLogger(SiriCommon.class);

  private static DatatypeFactory _dataTypeFactory = SiriTypeFactory.createDataTypeFactory();

  private JAXBContext _jaxbContext;

  protected SchedulingService _schedulingService;

  /**
   * We break this out as a separate service to assist with unit testing
   */
  protected HttpClientService _httpClientService;

  private String _identity;

  private String _url;

  private String _privateUrl;

  private String _expandedUrl;

  /**
   * If not null, overrides the default local address used by outgoing http
   * client connections. Useful if the connection needs to appear to come from a
   * specific port.
   */
  private InetAddress _localAddress;

  protected ELogRawXmlType _logRawXmlType = ELogRawXmlType.NONE;

  protected boolean _formatOutputXmlByDefault = false;

  private AtomicInteger _requestCount = new AtomicInteger();

  public SiriCommon() {

    try {
      _log.info("Loading JAXB context.  This may take a few seconds...");
      _jaxbContext = JAXBContext.newInstance("uk.org.siri:org.onebusaway.siri:uk.org.siri.siri");
      _log.info("Loading JAXB context complete.");
    } catch (JAXBException ex) {
      throw new SiriSerializationException(ex);
    }

    _identity = UUID.randomUUID().toString();
  }

  @Inject
  public void setSchedulingService(SchedulingService schedulingService) {
    _schedulingService = schedulingService;
  }

  @Inject
  public void setHttpClientService(HttpClientService httpClientService) {
    _httpClientService = httpClientService;
  }

  /**
   * @return your SIRI participant identity, used to identify your SIRI endpoint
   *         in most requests and responses
   */
  public String getIdentity() {
    return _identity;
  }

  /**
   * Set the SIRI participant identity used to identify your SIRI endpoint in
   * most requests and responses.
   * 
   * @param identity your SIRI participant identity
   */
  public void setIdentity(String identity) {
    _identity = identity;
  }

  /**
   * The public url our client will listen to and expose for url callbacks from
   * the SIRI server. Only used when using publish / subscribe methods. See also
   * {@link #getPrivateUrl()}.
   * 
   * @return the client url
   */
  public String getUrl() {
    return _url;
  }

  /**
   * The public url our client will listen to and expose for url callbacks from
   * the SIRI server. Only used when using publish / subscribe methods. See also
   * {@link #setPrivateUrl(String)}.
   * 
   * @param url
   * 
   */
  public void setUrl(String url) {
    _url = url;
    /**
     * Perform wildcard hostname expansion on our consumer address
     */
    _expandedUrl = replaceHostnameWildcardWithPublicHostnameInUrl(_url);
    checkLocalAddress();
  }

  /**
   * See the discussion for {@link #setPrivateUrl(String)}.
   * 
   * @return the private client url
   */
  public String getPrivateUrl() {
    return _privateUrl;
  }

  /**
   * In some cases, we may wish to listen for incoming SIRI data from the server
   * on a different local URL than the URL we publish externally to the SIRI
   * server (see {@link #setUrl(String)}). For example, your firewall or NAT
   * setup might require a separate public and private client url. If set, the
   * privateClientUrl will control how we actually listen for incoming SIRI
   * service deliveries, separate from the url we announce to the server.
   * 
   * If privateClientUrl is not set, we'll default to using the public clientUrl
   * (see {@link #setUrl(String)}).
   * 
   * @param privateClientUrl
   */
  public void setPrivateUrl(String privateClientUrl) {
    _privateUrl = privateClientUrl;
    checkLocalAddress();
  }

  /**
   * The internal URL that the client should bind to for incoming pub-sub
   * connection. This defaults to {@link #getUrl()}, unless
   * {@link #getPrivateUrl()} has been specified.
   * 
   * @param expandHostnameWildcard
   * 
   * @return
   */
  public URL getInternalUrlToBind(boolean expandHostnameWildcard) {

    String clientUrl = _url;
    if (_privateUrl != null)
      clientUrl = _privateUrl;

    if (expandHostnameWildcard)
      clientUrl = replaceHostnameWildcardWithPublicHostnameInUrl(clientUrl);

    return url(clientUrl);
  }

  /**
   * If your machine has multiple addresses and you need your SIRI HTTP requests
   * to look like they are coming from a specific address, this address will be
   * used.
   * 
   * @return
   */
  public InetAddress getLocalAddress() {
    return _localAddress;
  }

  /**
   * If your machine has multiple addresses and you need your SIRI HTTP requests
   * to look like they are coming from a specific address, use this method to
   * indicate which local address to use.
   * 
   * @param localAddress the address to use
   */
  public void setLocalAddress(InetAddress localAddress) {
    _localAddress = localAddress;
  }

  /**
   * 
   * @return how raw XML of SIRI messages is logged
   */
  public ELogRawXmlType getLogRawXmlType() {
    return _logRawXmlType;
  }

  /**
   * Determine how raw XML of SIRI messages is internally logged
   * 
   * @param logRawXmlType the logging type
   */
  public void setLogRawXmlType(ELogRawXmlType logRawXmlType) {
    _logRawXmlType = logRawXmlType;
  }

  /**
   * @return true if serialized XML messages will be pretty-print formatted by
   *         default.
   */
  public boolean isFormatOutputXmlByDefault() {
    return _formatOutputXmlByDefault;
  }

  /**
   * Determine if serialized XML messages will be pretty-print formatted by
   * default.
   * 
   * @param formatOutputXmlByDefault true if output formatting should be applied
   */
  public void setFormatOutputXmlByDefault(boolean formatOutputXmlByDefault) {
    _formatOutputXmlByDefault = formatOutputXmlByDefault;
  }

  /**
   * It's up to sub-classes to implement this properly. We just provide it here
   * to make {@link SiriCommon} appropriate as for exporting, as opposed to
   * working with SiriClient and SiriServer by themselves.
   */
  @Override
  public void handleRawRequest(Reader reader, Writer writer) {

  }

  @Override
  public void getStatus(Map<String, String> status) {
    status.put("siri.common.requestCounter",
        Integer.toString(_requestCount.get()));
  }

  /****
   * Protected Methods
   ****/

  /**
   * Submit the specified {@link SiriClientRequest}, filling in any Siri
   * data-structures with appropriate default values, versioning the payload as
   * appropriate for the endpoint, and submitting the request over HTTP. Any
   * response is parsed, convered back to the latest SIRI version, and returned.
   * 
   * @param <T>
   * @param request the SIRI client request
   * @return any response from the endpoint, or null if none received
   */
  @SuppressWarnings("unchecked")
  protected <T> T processRequestWithResponse(SiriClientRequest request) {

    _requestCount.incrementAndGet();

    Siri payload = request.getPayload();

    /**
     * We make a deep copy of the SIRI payload so that when we fill in values
     * for the request, the original is unmodified, making it reusable.
     */
    payload = SiriLibrary.copy(payload);

    fillAllSiriStructures(payload);

    if (payload.getSubscriptionRequest() != null)
      fillSubscriptionRequestStructure(request,
          payload.getSubscriptionRequest());

    /**
     * We potentially need to translate the Siri payload to an older version of
     * the specification, as requested by the caller
     */
    SiriVersioning versioning = SiriVersioning.getInstance();
    Object versionedPayload = versioning.getPayloadAsVersion(payload,
        request.getTargetVersion());

    String content = marshallToString(versionedPayload);

    if (isRawDataLogged(payload)) {
      _log.info("logging raw xml request:\n=== REQUEST BEGIN ===\n" + content
          + "\n=== REQUEST END ===");
    }

    HttpResponse response = processRawContentRequestWithResponse(request,
        payload, content);

    if (response == null)
      return null;

    HttpEntity entity = response.getEntity();

    String responseContent = null;
    Object responseData = null;

    try {

      Reader responseReader = new InputStreamReader(entity.getContent());

      _log.debug("response content length: {}", entity.getContentLength());

      /**
       * Opportunistically skip capturing the response in an intermediate string
       * if we don't have to
       */
      if (_logRawXmlType != ELogRawXmlType.NONE) {
        StringBuilder b = new StringBuilder();
        responseReader = copyReaderToStringBuilder(responseReader, b);
        responseContent = b.toString();

      }

      if (entity.getContentLength() != 0) {
        responseData = unmarshall(responseReader);
        responseData = versioning.getPayloadAsVersion(responseData,
            versioning.getDefaultVersion());
      }

    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }

    if (responseData != null && responseData instanceof Siri) {

      Siri siri = (Siri) responseData;

      if (isRawDataLogged(siri)) {
        _log.info("logging raw xml response:\n=== RESPONSE BEGIN ===\n"
            + responseContent + "\n=== RESPONSE END ===");
      }

      handleSiriResponse(siri, false);
    }

    return (T) responseData;
  }

  /**
   * The specified client request is processed asynchronously on another thread.
   * 
   * @param request the SIRI client request
   */
  protected void processRequestWithAsynchronousResponse(
      SiriClientRequest request) {

    AsynchronousClientConnectionAttempt attempt = new AsynchronousClientConnectionAttempt(
        request);
    _schedulingService.submit(attempt);
  }

  /**
   * Override this method to provide custom behavior for processing a response
   * from a SIRI endoint.
   * 
   * @param siri the payload
   * @param asynchronousResponse true if the response was received
   *          asynchronously, otherwise false
   */
  protected void handleSiriResponse(Siri siri, boolean asynchronousResponse) {

  }

  /****
   * 
   ****/

  /**
   * Fill in all SIRI data-strutures with appropriate default values as needed.
   * 
   * @param siri the SIRI payload
   */
  protected void fillAllSiriStructures(Siri siri) {

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

    fillResponseStructure(siri.getCapabilitiesResponse());
    fillResponseStructure(siri.getCheckStatusResponse());
    fillServiceDelivery(siri.getServiceDelivery());
    fillResponseStructure(siri.getSubscriptionResponse());
    fillResponseStructure(siri.getTerminateSubscriptionResponse());
  }

  /**
   * Fill in the {@link SubscriptionRequest} with appropriate default values as
   * needed.
   * 
   * @param request the SIRI client request associated with the subscription
   *          request
   * @param subscriptionRequest the subscription request to fill in
   */
  protected void fillSubscriptionRequestStructure(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    if (subscriptionRequest == null)
      return;

    int heartbeatInterval = request.getHeartbeatInterval();
    if (heartbeatInterval > 0) {

      Duration interval = _dataTypeFactory.newDuration(heartbeatInterval * 1000);
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
          Date initialTerminationTime = new Date(System.currentTimeMillis()
              + request.getInitialTerminationDuration());
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

  /**
   * Fill in the request with appropriate default values as needed.
   * 
   * @param request the request
   */
  protected void fillRequestStructure(RequestStructure request) {

    if (request == null)
      return;

    request.setRequestorRef(SiriTypeFactory.particpantRef(_identity));

    request.setAddress(_expandedUrl);

    if (request.getMessageIdentifier() == null
        || request.getMessageIdentifier().getValue() == null) {
      MessageQualifierStructure messageIdentifier = SiriTypeFactory.randomMessageId();
      request.setMessageIdentifier(messageIdentifier);
    }

    if (request.getRequestTimestamp() == null)
      request.setRequestTimestamp(new Date());
  }

  /**
   * Fill in the request with appropriate default values as needed.
   * 
   * TODO: It sure would be nice if {@link ServiceRequest} was a sub-class of
   * {@link RequestStructure}
   * 
   * @param request
   */
  protected void fillServiceRequestStructure(ServiceRequest request) {

    if (request == null)
      return;

    request.setRequestorRef(SiriTypeFactory.particpantRef(_identity));

    request.setAddress(_expandedUrl);

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

  /**
   * Fill in the requests with appropriate default values as needed.
   * 
   * @param <T>
   * @param requests
   */
  protected <T extends AbstractFunctionalServiceRequestStructure> void fillAbstractFunctionalServiceRequestStructures(
      List<T> requests) {
    for (AbstractFunctionalServiceRequestStructure request : requests)
      fillAbstractFunctionalServiceRequestStructure(request);
  }

  /**
   * Fill in the request with appropriate default values as needed.
   * 
   * @param request
   */
  protected void fillAbstractFunctionalServiceRequestStructure(
      AbstractFunctionalServiceRequestStructure request) {

    fillAbstractRequestStructure(request);
  }

  /**
   * Fill in the request with appropriate default values as needed.
   * 
   * @param request
   */
  protected void fillAbstractRequestStructure(AbstractRequestStructure request) {
    if (request.getRequestTimestamp() == null)
      request.setRequestTimestamp(new Date());
  }

  protected void fillServiceDelivery(ServiceDelivery serviceDelivery) {
    fillResponseStructure(serviceDelivery);
  }

  /****
   * Private Methods
   ****/

  private void fillResponseStructure(ResponseEndpointStructure response) {

    if (response == null)
      return;

    if (response.getAddress() != null)
      response.setAddress(_expandedUrl);

    if (response.getResponderRef() != null)
      response.setResponderRef(SiriTypeFactory.particpantRef(_identity));

    fillGenericResponseStructure(response);
  }

  private void fillResponseStructure(ProducerResponseStructure response) {

    if (response == null)
      return;

    if (response.getAddress() == null)
      response.setAddress(_expandedUrl);

    if (response.getProducerRef() == null)
      response.setProducerRef(SiriTypeFactory.particpantRef(_identity));

    if (response.getResponseMessageIdentifier() == null)
      response.setResponseMessageIdentifier(SiriTypeFactory.randomMessageId());

    fillGenericResponseStructure(response);
  }

  private void fillResponseStructure(CheckStatusResponseStructure response) {

    if (response == null)
      return;

    if (response.getAddress() != null)
      response.setAddress(_expandedUrl);

    if (response.getProducerRef() != null)
      response.setProducerRef(SiriTypeFactory.particpantRef(_identity));

    if (response.getResponseMessageIdentifier() == null)
      response.setResponseMessageIdentifier(SiriTypeFactory.randomMessageId());

    fillGenericResponseStructure(response);
  }

  private void fillGenericResponseStructure(ResponseStructure response) {

    if (response == null)
      return;

    if (response.getResponseTimestamp() == null)
      response.setResponseTimestamp(new Date());
  }

  /****
   * 
   ****/

  /**
   * 
   * @param payload the SIRI payload
   * @return true if the specified payload should be logged, as determined by
   *         the {@link #getLogRawXmlType()} settings.
   */
  protected boolean isRawDataLogged(Siri payload) {
    switch (_logRawXmlType) {
      case NONE:
        return false;
      case ALL:
        return true;
      case DATA:
        return payload.getServiceDelivery() != null;
      case CONTROL:
        return payload.getServiceDelivery() == null;
      default:
        throw new IllegalStateException("unknown ELogRawXmlType="
            + _logRawXmlType);
    }
  }

  /****
   * 
   ****/

  /**
   * Method to unmarshall an {@link InputStream} into a high-level SIRI
   * data-structure using JAXB. Typically, you don't call this directly.
   * 
   * @param <T>
   * @param in
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T unmarshall(InputStream in) {
    try {
      Unmarshaller unmarshaller = _jaxbContext.createUnmarshaller();
      return (T) unmarshaller.unmarshal(in);
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }
  }

  /**
   * Method to unmarshall an {@link Reader} into a high-level SIRI
   * data-structure using JAXB. Typically, you don't call this directly.
   * 
   * @param <T>
   * @param reader
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T unmarshall(Reader reader) {
    try {
      Unmarshaller unmarshaller = _jaxbContext.createUnmarshaller();
      return (T) unmarshaller.unmarshal(reader);
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }
  }

  /**
   * Marshall the specified object to the target {@link Writer} using JAXB.
   * 
   * @param object
   * @param writer
   */
  public void marshall(Object object, Writer writer) {
    marshall(object, writer, _formatOutputXmlByDefault);
  }

  /**
   * Marshall the specified object to the target {@link Writer} using JAXB.
   * 
   * @param object
   * @param writer
   * @param formatOutput if true, the serialized XML will be pretty-printed
   */
  public void marshall(Object object, Writer writer, boolean formatOutput) {
    try {
      Marshaller m = _jaxbContext.createMarshaller();
      if (formatOutput) {
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      }
      m.marshal(object, writer);
    } catch (JAXBException ex) {
      throw new SiriSerializationException(ex);
    }
  }

  /**
   * Marshall the specified object as a String using JAXB.
   * 
   * @param object
   * @return the String representation of the specified Object
   */
  public String marshallToString(Object object) {
    return marshallToString(object, _formatOutputXmlByDefault);
  }

  /**
   * Marshall the specified object as a String using JAXB.
   * 
   * @param object
   * @param formatOutput if true, the serialized XML will be pretty-printed
   * @return the String representation of the specified Object
   */
  public String marshallToString(Object object, boolean formatOutput) {
    StringWriter writer = new StringWriter();
    marshall(object, writer, formatOutput);
    return writer.toString();
  }

  /****
   * Protected Methods
   ****/

  protected ScheduledExecutorService createExecutor() {
    return Executors.newSingleThreadScheduledExecutor();
  }

  /**
   * This method encapsulates our reconnection behavior around the call to
   * {@link #sendHttpRequest(String, String)}.
   * 
   * @param request
   * @param payload
   * @param content
   * @return
   */
  protected HttpResponse processRawContentRequestWithResponse(
      SiriClientRequest request, Siri payload, String content) {

    String url = getUrlForRequest(request);

    if (request.getReconnectionAttempts() != 0) {

      try {

        HttpResponse response = sendHttpRequest(url, content);

        /**
         * Reset our connection error count and note that we've successfully
         * reconnected if the we've had problems before
         */
        if (request.getConnectionErrorCount() > 0)
          _log.info("successfully reconnected to " + url);
        request.resetConnectionErrorCount();

        return response;

      } catch (SiriConnectionException ex) {

        String message = "error connecting to " + url
            + " (remainingConnectionAttempts="
            + request.getRemainingReconnectionAttempts()
            + " connectionErrorCount=" + request.getConnectionErrorCount()
            + ")";

        /**
         * We display the full exception on the first connection error, but hide
         * it on recurring errors
         */
        if (request.getConnectionErrorCount() == 0) {
          _log.warn(message, ex);
        } else {
          _log.warn(message);
        }

        request.incrementConnectionErrorCount();

        cleanupFailedRequest(request, payload);
        reattemptRequestIfApplicable(request);

        /**
         * Note: we swallow up the exception here, meaning the client won't know
         * there is an error. Might there be situations where the client wants
         * to know it was an error, even if they've specified reconnection
         * semantics?
         */
        return null;
      }

    } else {

      return sendHttpRequest(url, content);
    }
  }

  /**
   * Determine which URL should be used for a request. Recall that a SIRI
   * endpoint can have separate URLs for subscription management and
   * check-status requests.
   * 
   * @param request
   * @return
   */
  protected String getUrlForRequest(SiriClientRequest request) {
    Siri payload = request.getPayload();
    if (payload.getCheckStatusRequest() != null
        && request.getCheckStatusUrl() != null)
      return request.getCheckStatusUrl();
    if (payload.getTerminateSubscriptionRequest() != null
        && request.getManageSubscriptionUrl() != null)
      return request.getManageSubscriptionUrl();
    return request.getTargetUrl();
  }

  /**
   * Construct an HTTP POST request, send it, and decode the response.
   * 
   * TODO: We could probably encapsulate this even further to make it easier to
   * replace the HTTP client implementation or support additional transport
   * mechanisms.
   * 
   * @param url the target url where we will POST
   * @param content the content of the POST request
   * @return the response
   */
  protected HttpResponse sendHttpRequest(String url, String content) {

    DefaultHttpClient client = new DefaultHttpClient();

    /**
     * Override the default local address used for outgoing http client
     * connections, if specified
     */
    if (_localAddress != null) {
      HttpParams params = client.getParams();
      params.setParameter(ConnRoutePNames.LOCAL_ADDRESS, _localAddress);
    }

    HttpPost post = new HttpPost(url);

    try {
      post.setEntity(new StringEntity(content));
    } catch (UnsupportedEncodingException ex) {
      throw new SiriSerializationException(ex);
    }

    HttpResponse response = _httpClientService.executeHttpMethod(client, post);
    StatusLine statusLine = response.getStatusLine();

    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        try {
          BufferedReader reader = new BufferedReader(new InputStreamReader(
              entity.getContent()));
          StringBuilder b = new StringBuilder();
          String line = null;

          while ((line = reader.readLine()) != null)
            b.append(line).append('\n');
          _log.warn("error connecting to url " + post.getURI() + " statusCode="
              + statusLine.getStatusCode() + "\nrequestBody=" + content
              + "\nresponseBody=" + b.toString());
        } catch (IOException ex) {
          _log.warn("error reading http response", ex);
        }
      }
      /**
       * TODO: This is a temporary hack to keep a bad client from bailing...
       */
      if (statusLine.getStatusCode() != HttpStatus.SC_BAD_REQUEST) {
        throw new SiriConnectionException("error connecting to url "
            + post.getURI() + " statusCode=" + statusLine.getStatusCode());
      } else {
        _log.warn("statusCode=" + statusLine.getStatusCode()
            + " so ignoring for now");
      }
    }

    return response;
  }

  /**
   * Method provides opportunity to clean up a failed client request.
   * 
   * @param request
   * @param payload
   */
  protected void cleanupFailedRequest(SiriClientRequest request, Siri payload) {

  }

  /**
   * In an instance where a {@link SiriClientRequest} has a connection error, or
   * perhaps when an appropriate response is not received before a timeout, this
   * method will attempt to resend the request based on the
   * {@link SiriClientRequest#getRemainingReconnectionAttempts()} behavior.
   * 
   * The reconnect will be attempted asynchronously after the
   * {@link SiriClientRequest#getReconnectionInterval()} has passed.
   * 
   * @param request the client request to potentially reconnect
   */
  protected void reattemptRequestIfApplicable(SiriClientRequest request) {

    if (request.getRemainingReconnectionAttempts() == 0)
      return;

    /**
     * We have some reconnection attempts remaining, so we schedule another
     * connection attempt
     */
    request.decrementRemainingReconnctionAttempts();

    AsynchronousClientConnectionAttempt asyncAttempt = new AsynchronousClientConnectionAttempt(
        request);
    _schedulingService.schedule(asyncAttempt,
        request.getReconnectionInterval(), TimeUnit.SECONDS);
  }

  protected Reader copyReaderToStringBuilder(Reader responseReader,
      StringBuilder b) throws IOException {
    char[] buffer = new char[1024];
    while (true) {
      int rc = responseReader.read(buffer);
      if (rc == -1)
        break;
      b.append(buffer, 0, rc);
    }

    responseReader.close();
    responseReader = new StringReader(b.toString());
    return responseReader;
  }

  /**
   * Convenience method that hides the {@link MalformedURLException} thrown when
   * creating a URL object.
   * 
   * @param url string representation of a URL
   * @return the actual URL
   */
  protected URL url(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException ex) {
      throw new SiriException("bad url " + url, ex);
    }
  }

  /**
   * If the user has specified a wildcard "*" in their {@link #getUrl()},
   * indicating that they want to bind to all interfaces on their machine, we
   * still need a hostname for when we broadcast that URL to SIRI endpoints for
   * pub-sub callbacks. This method constructs a URL with the "*" replaced with
   * the machine's hostname.
   * 
   * @param url
   * @return
   */
  protected String replaceHostnameWildcardWithPublicHostnameInUrl(String url) {

    try {
      URL asURL = new URL(url);
      if (asURL.getHost().equals("*")) {
        InetAddress address = Inet4Address.getLocalHost();
        String hostname = address.getHostName();
        return url.replace("*", hostname);
      }
    } catch (UnknownHostException e) {

    } catch (MalformedURLException e) {

    }

    return url;
  }

  /**
   * Determine if a specific local address has been specified in the internal
   * URL, indicating that we should use that as our source address when making
   * HTTP client requests. Important if you need your SIRI HTTP requests to look
   * like they are coming from a particular address, especially when your
   * machine has more than one address.
   */
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

  /**
   * A runnable task that attempts a connection from a SIRI client to a SIRI
   * server. Handles reconnection semantics.
   * 
   * @author bdferris
   */
  private class AsynchronousClientConnectionAttempt implements Runnable {

    private final SiriClientRequest request;

    public AsynchronousClientConnectionAttempt(SiriClientRequest request) {
      this.request = request;
    }

    @Override
    public void run() {

      try {
        processRequestWithResponse(request);
      } catch (Throwable ex) {
        _log.error("error executing asynchronous client request", ex);
      }
    }
  }
}
