package org.onebusaway.siri.repeater.impl;

import java.io.StringReader;
import java.io.Writer;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.onebusaway.siri.repeater.services.SiriSerializationService;
import org.springframework.stereotype.Component;

@Component
public class SiriSerializationServiceImpl implements SiriSerializationService {

  private JAXBContext _jaxbContext;

  @PostConstruct
  public void setup() throws JAXBException {
    _jaxbContext = JAXBContext.newInstance("uk.org.siri.siri");
  }

  @Override
  public Object unmarshal(String body) {
    try {
      Unmarshaller u = _jaxbContext.createUnmarshaller();
      return u.unmarshal(new StringReader(body));
    } catch (JAXBException ex) {
      throw new IllegalStateException("error unmarshalling object", ex);
    }
  }

  @Override
  public void marshall(Object object, Writer writer) {
    try {
      Marshaller m = _jaxbContext.createMarshaller();
      m.marshal(object, writer);
    } catch (JAXBException ex) {
      throw new IllegalStateException("error marshalling object", ex);
    }
  }
}
