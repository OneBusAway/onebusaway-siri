package org.onebusaway.siri.core;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;

public class SiriClient extends SiriCommon {

  private String _identity;

  private String _targetUrl;

  public SiriClient() {
    _identity = UUID.randomUUID().toString();
  }

  public void setIdentity(String identity) {
    _identity = identity;
  }

  public void setTargetUrl(String targetUrl) {
    _targetUrl = targetUrl;
  }

  public ServiceDelivery handlServiceRequestWithResponse(ServiceRequest request) {

    ParticipantRefStructure identity = new ParticipantRefStructure();
    identity.setValue(_identity);
    request.setRequestorRef(identity);

    request.setAddress(null);

    MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
    messageIdentifier.setValue(UUID.randomUUID().toString());
    request.setMessageIdentifier(messageIdentifier);

    request.setRequestTimestamp(new Date());

    return handlServiceRequestWithResponse(_targetUrl, request);
  }

  public ServiceDelivery handlServiceRequestWithResponse(String url,
      ServiceRequest request) throws SiriException {

    StringWriter writer = new StringWriter();
    marshall(request, writer);

    DefaultHttpClient client = new DefaultHttpClient();

    HttpPost post = new HttpPost(url);

    try {
      post.setEntity(new StringEntity(writer.toString()));
    } catch (UnsupportedEncodingException ex) {
      throw new SiriSerializationException(ex);
    }

    HttpResponse response = executeHttpMethod(client, post);
    StatusLine statusLine = response.getStatusLine();

    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new SiriConnectionException("error connecting to url "
          + post.getURI() + " statusCode=" + statusLine.getStatusCode());
    }

    HttpEntity entity = response.getEntity();
    try {
      return unmarshall(entity.getContent());
    } catch (Exception ex) {
      throw new SiriSerializationException(ex);
    }
  }

  /****
   * Private Methods
   ****/

  private HttpResponse executeHttpMethod(DefaultHttpClient client, HttpPost post)
      throws SiriException {
    try {
      return client.execute(post);
    } catch (Exception ex) {
      throw new SiriConnectionException("error connecting to url "
          + post.getURI(),ex);
    }
  }

}
