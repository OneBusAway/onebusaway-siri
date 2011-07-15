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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiriCommon {

  private static Logger _log = LoggerFactory.getLogger(SiriCommon.class);

  private JAXBContext _jaxbContext;

  /**
   * If not null, overrides the default local address used by outgoing http
   * client connections. Useful if the connection needs to appear to come from a
   * specific port.
   */
  private InetAddress _localAddress;

  public SiriCommon() {

    try {
      _jaxbContext = JAXBContext.newInstance("uk.org.siri.siri:uk.org.siri:org.onebusaway.siri");
    } catch (JAXBException ex) {
      throw new SiriSerializationException(ex);
    }
  }

  public void setLocalAddress(InetAddress localAddress) {
    _localAddress = localAddress;
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

  public String marshallToString(Object object){
    StringWriter writer = new StringWriter();
    marshall(object, writer);
    return writer.toString();
  }
  
  /****
   * Protected Methods
   ****/

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
      InetAddress address = Inet4Address.getLocalHost();
      String hostname = address.getHostName();
      return url.replace("*", hostname);
    } catch (UnknownHostException e) {
      return url;
    }
  }
}
