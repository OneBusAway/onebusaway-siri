package org.onebusaway.siri.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;
import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.exceptions.SiriSerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiriCommon {

  private static Logger _log = LoggerFactory.getLogger(SiriCommon.class);

  private JAXBContext _jaxbContext;

  public SiriCommon() {

    try {
      _jaxbContext = JAXBContext.newInstance("uk.org.siri.siri:org.onebusaway.siri");
    } catch (JAXBException ex) {
      throw new SiriSerializationException(ex);
    }
  }

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

  /****
   * Protected Methods
   ****/

  protected HttpResponse sendHttpRequest(String url, Object request) {

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
              + statusLine.getStatusCode() + "\n" + b.toString());
        } catch (IOException ex) {
          _log.warn("error reading http response", ex);
        }
      }
      throw new SiriConnectionException("error connecting to url "
          + post.getURI() + " statusCode=" + statusLine.getStatusCode());
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

  protected URL url(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException ex) {
      throw new SiriException("bad url " + url, ex);
    }
  }
}
