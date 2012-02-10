/**
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

import static junit.framework.Assert.assertEquals;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathExists;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.onebusaway.siri.core.SiriCommon.AsynchronousClientRequest;
import org.onebusaway.siri.core.services.HttpClientService;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.subscriptions.client.SiriClientSubscriptionManager;
import org.onebusaway.siri.core.versioning.ESiriVersion;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.CheckStatusResponseStructure;
import uk.org.siri.siri.Siri;

public class SiriClientTest {

  private SiriClient _client;

  private HttpClientService _httpClientService;

  private SiriClientSubscriptionManager _subscriptionManager;

  private SchedulingService _schedulingService;

  @BeforeClass
  public static void setupOnce() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("s", "http://www.siri.org.uk/siri");
    NamespaceContext ctx = new SimpleNamespaceContext(m);
    XMLUnit.setXpathNamespaceContext(ctx);
  }

  @Before
  public void setup() {
    _client = new SiriClient();
    _client.setIdentity("somebody");

    _httpClientService = Mockito.mock(HttpClientService.class);
    _client.setHttpClientService(_httpClientService);

    _subscriptionManager = Mockito.mock(SiriClientSubscriptionManager.class);
    _client.setSubscriptionManager(_subscriptionManager);

    _schedulingService = Mockito.mock(SchedulingService.class);
    _client.setSchedulingService(_schedulingService);
  }

  @Test
  public void testHandleRequestWithResponseForCheckStatusRequestAndResponse()
      throws IllegalStateException, IOException, XpathException, SAXException {

    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl("http://localhost/");
    request.setTargetVersion(ESiriVersion.V1_3);

    Siri payload = new Siri();
    request.setPayload(payload);

    CheckStatusRequestStructure checkStatusRequest = new CheckStatusRequestStructure();
    payload.setCheckStatusRequest(checkStatusRequest);

    StringBuilder b = new StringBuilder();
    b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    b.append("<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"1.3\">");
    b.append("  <CheckStatusResponse>");
    b.append("  </CheckStatusResponse>");
    b.append("</Siri>");

    HttpResponse response = createResponse();
    response.setEntity(new StringEntity(b.toString()));

    Mockito.when(
        _httpClientService.executeHttpMethod(Mockito.any(HttpClient.class),
            Mockito.any(HttpUriRequest.class))).thenReturn(response);

    Siri siriResponse = _client.handleRequestWithResponse(request);

    /**
     * Verify that the http client was called and capture the request
     */
    ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
    Mockito.verify(_httpClientService).executeHttpMethod(
        Mockito.any(HttpClient.class), captor.capture());

    /**
     * Verify that our CheckStatusResponse was properly passed on to the
     * subscription manager
     */
    Mockito.verify(_subscriptionManager).handleCheckStatusNotification(
        Mockito.any(CheckStatusResponseStructure.class));

    /**
     * Verify the raw parameters of the request
     */
    HttpUriRequest uriRequest = captor.getValue();
    assertEquals("http://localhost/", uriRequest.getURI().toString());
    assertEquals("POST", uriRequest.getMethod());

    /**
     * Verify the request content
     */
    HttpPost post = (HttpPost) uriRequest;
    HttpEntity entity = post.getEntity();
    String content = getHttpEntityAsString(entity);
    Document doc = XMLUnit.buildControlDocument(content);

    assertXpathExists("/s:Siri", doc);
    assertXpathEvaluatesTo("1.3", "/s:Siri/@version", doc);
    assertXpathExists("/s:Siri/s:CheckStatusRequest", content);
    assertXpathExists("/s:Siri/s:CheckStatusRequest/s:RequestTimestamp", doc);
    assertXpathExists("/s:Siri/s:CheckStatusRequest/s:Address", doc);
    assertXpathEvaluatesTo("somebody",
        "/s:Siri/s:CheckStatusRequest/s:RequestorRef", doc);
    assertXpathExists("/s:Siri/s:CheckStatusRequest/s:MessageIdentifier", doc);

    // Verify that the message id is a UUID
    String messageId = evaluateXPath(
        "/s:Siri/s:CheckStatusRequest/s:MessageIdentifier", doc);
    UUID.fromString(messageId);

    /**
     * Verify that the CheckStatusResponse was properly received and parsed
     */
    CheckStatusResponseStructure checkStatusResponse = siriResponse.getCheckStatusResponse();
    assertNotNull(checkStatusResponse);
  }

  @Test
  public void testHandleRequestWithNoPolling() throws Exception {

    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl("http://localhost/");
    request.setTargetVersion(ESiriVersion.V1_3);
    request.setSubscribe(false);
    request.setPollInterval(0);

    Siri payload = new Siri();
    request.setPayload(payload);

    CheckStatusRequestStructure checkStatusRequest = new CheckStatusRequestStructure();
    payload.setCheckStatusRequest(checkStatusRequest);

    StringBuilder b = new StringBuilder();
    b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    b.append("<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"1.3\">");
    b.append("</Siri>");

    HttpResponse response = createResponse();
    response.setEntity(new StringEntity(b.toString()));

    Mockito.when(
        _httpClientService.executeHttpMethod(Mockito.any(HttpClient.class),
            Mockito.any(HttpUriRequest.class))).thenReturn(response);

    _client.handleRequestWithResponse(request);

    /**
     * Verify that the http client was called and capture the request
     */
    ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
    Mockito.verify(_httpClientService).executeHttpMethod(
        Mockito.any(HttpClient.class), captor.capture());

    Mockito.verifyNoMoreInteractions(_schedulingService);
  }

  @Test
  public void testHandleRequestWithPolling() throws Exception {

    SiriClientRequest request = new SiriClientRequest();
    request.setTargetUrl("http://localhost/");
    request.setTargetVersion(ESiriVersion.V1_3);
    request.setSubscribe(false);
    request.setPollInterval(30);

    Siri payload = new Siri();
    request.setPayload(payload);

    CheckStatusRequestStructure checkStatusRequest = new CheckStatusRequestStructure();
    payload.setCheckStatusRequest(checkStatusRequest);

    StringBuilder b = new StringBuilder();
    b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    b.append("<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"1.3\">");
    b.append("</Siri>");

    HttpResponse response = createResponse();
    response.setEntity(new StringEntity(b.toString()));

    Mockito.when(
        _httpClientService.executeHttpMethod(Mockito.any(HttpClient.class),
            Mockito.any(HttpUriRequest.class))).thenReturn(response);

    _client.handleRequestWithResponse(request);

    /**
     * Verify that the http client was called and capture the request
     */
    ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
    Mockito.verify(_httpClientService).executeHttpMethod(
        Mockito.any(HttpClient.class), captor.capture());

    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    Mockito.verify(_schedulingService).schedule(taskCaptor.capture(),
        Mockito.eq(30L), Mockito.eq(TimeUnit.SECONDS));
    SiriCommon.AsynchronousClientRequest task = (AsynchronousClientRequest) taskCaptor.getValue();
    assertSame(request, task.getRequest());
  }

  /*
   * @Test public void testHandleRawRequest() { fail("Not yet implemented"); }
   * 
   * @Test public void testHandleRequest() { fail("Not yet implemented"); }
   * 
   * @Test public void testHandleRequestReconnectIfApplicable() {
   * fail("Not yet implemented"); }
   */

  private HttpResponse createResponse() {
    BasicStatusLine line = new BasicStatusLine(HttpVersion.HTTP_1_1,
        HttpStatus.SC_OK, "");
    return new BasicHttpResponse(line);
  }

  private String getHttpEntityAsString(HttpEntity entity)
      throws IllegalStateException, IOException {

    BufferedReader reader = new BufferedReader(new InputStreamReader(
        entity.getContent()));
    StringBuilder b = new StringBuilder();
    String line = null;

    while ((line = reader.readLine()) != null)
      b.append(line).append('\n');

    return b.toString();
  }

  private String evaluateXPath(String select, Document document)
      throws XpathException {
    XpathEngine simpleXpathEngine = XMLUnit.newXpathEngine();
    return simpleXpathEngine.evaluate(select, document);
  }
}
