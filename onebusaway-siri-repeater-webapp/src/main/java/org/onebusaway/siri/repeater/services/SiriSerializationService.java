package org.onebusaway.siri.repeater.services;

import java.io.Writer;

public interface SiriSerializationService {

  public Object unmarshal(String body);

  public void marshall(Object object, Writer writer);
}
