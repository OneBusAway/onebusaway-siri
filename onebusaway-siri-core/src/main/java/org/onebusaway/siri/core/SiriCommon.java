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
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
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
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionContextStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;

public class SiriCommon {

  private static Logger _log = LoggerFactory.getLogger(SiriCommon.class);

  private JAXBContext _jaxbContext;

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

  protected boolean _logRawXml = false;

  protected ScheduledExecutorService _executor;

  public SiriCommon() {

    try {
      _jaxbContext = JAXBContext.newInstance("uk.org.siri:org.onebusaway.siri:uk.org.siri.siri");
    } catch (JAXBException ex) {
      throw new SiriSerializationException(ex);
    }

    _identity = UUID.randomUUID().toString();

  }

  public String getIdentity() {
    return _identity;
  }

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

  public InetAddress getLocalAddress() {
    return _localAddress;
  }

  public void setLocalAddress(InetAddress localAddress) {
    _localAddress = localAddress;
  }

  public boolean isLogRawXml() {
    return _logRawXml;
  }

  public void setLogRawXml(boolean logRawXml) {
    _logRawXml = logRawXml;
  }

  public ScheduledExecutorService getExecutor() {
    return _executor;
  }

  /****
   * 
   ****/

  public void start() {
    _executor = createExecutor();
  }

  public void stop() {
    if (_executor != null)
      _executor.shutdownNow();
  }

  /****
   * 
   * @param <T>
   * @param in
   * @return
   ****/

  @SuppressWarnings("unchecked")
  protected <T> T processRequestWithResponse(SiriClientRequest request) {

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

    if (_logRawXml)
      _log.info("logging raw xml request:\n=== REQUEST BEGIN ===\n" + content
          + "\n=== REQUEST END ===");

    HttpResponse response = sendHttpRequest(request.getTargetUrl(), content);

    HttpEntity entity = response.getEntity();

    Object responseData = null;
    try {

      Reader responseReader = new InputStreamReader(entity.getContent());

      _log.debug("response content length: {}", entity.getContentLength());

      if (_logRawXml) {
        StringBuilder b = new StringBuilder();
        responseReader = copyReaderToStringBuilder(responseReader, b);
        _log.info("logging raw xml response:\n=== RESPONSE BEGIN ===\n"
            + b.toString() + "\n=== RESPONSE END ===");
      }

      if (entity.getContentLength() != 0) {
        responseData = unmarshall(responseReader);
        responseData = versioning.getPayloadAsVersion(responseData,
            versioning.getDefaultVersion());
      }

    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }

    if (responseData != null) {
      if (responseData instanceof Siri) {
        Siri siri = (Siri) responseData;
        handleSiriResponse(siri, false);
      }
    }

    return (T) responseData;
  }

  protected void handleSiriResponse(Siri siri, boolean asynchronousResponse) {

  }

  /****
   * 
   ****/

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
    fillResponseStructure(siri.getServiceDelivery());
    fillResponseStructure(siri.getSubscriptionResponse());
    fillResponseStructure(siri.getTerminateSubscriptionResponse());
  }

  protected void fillSubscriptionRequestStructure(SiriClientRequest request,
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

  protected <T extends AbstractFunctionalServiceRequestStructure> void fillAbstractFunctionalServiceRequestStructures(
      List<T> requests) {
    for (AbstractFunctionalServiceRequestStructure request : requests)
      fillAbstractFunctionalServiceRequestStructure(request);
  }

  protected void fillAbstractFunctionalServiceRequestStructure(
      AbstractFunctionalServiceRequestStructure request) {

    fillAbstractRequestStructure(request);
  }

  protected void fillAbstractRequestStructure(AbstractRequestStructure request) {
    if (request.getRequestTimestamp() == null)
      request.setRequestTimestamp(new Date());
  }

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

    if (response.getAddress() != null)
      response.setAddress(_expandedUrl);

    if (response.getProducerRef() != null)
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

  @SuppressWarnings("unchecked")
  public <T> T unmarshall(InputStream in) {
    try {
      Unmarshaller unmarshaller = _jaxbContext.createUnmarshaller();
      return (T) unmarshaller.unmarshal(in);
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> T unmarshall(Reader reader) {
    try {
      Unmarshaller unmarshaller = _jaxbContext.createUnmarshaller();
      return (T) unmarshaller.unmarshal(reader);
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }
  }

  public void marshall(Object object, Writer writer) {
    try {
      Marshaller m = _jaxbContext.createMarshaller();
      m.marshal(object, writer);
    } catch (JAXBException ex) {
      throw new SiriSerializationException(ex);
    }
  }

  public String marshallToString(Object object) {
    StringWriter writer = new StringWriter();
    marshall(object, writer);
    return writer.toString();
  }

  /****
   * Protected Methods
   ****/

  protected ScheduledExecutorService createExecutor() {
    return Executors.newSingleThreadScheduledExecutor();
  }

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

    HttpResponse response = executeHttpMethod(client, post);
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

  protected HttpResponse executeHttpMethod(DefaultHttpClient client,
      HttpPost post) throws SiriException {
    try {
      return client.execute(post);
    } catch (Exception ex) {
      throw new SiriConnectionException("error connecting to url "
          + post.getURI(), ex);
    }
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

  protected URL url(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException ex) {
      throw new SiriException("bad url " + url, ex);
    }
  }

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

  protected DatatypeFactory createDataTypeFactory() {
    try {
      return DatatypeFactory.newInstance();
    } catch (DatatypeConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }
}
