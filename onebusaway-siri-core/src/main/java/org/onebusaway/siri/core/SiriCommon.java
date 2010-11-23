package org.onebusaway.siri.core;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

public class SiriCommon {

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
}
