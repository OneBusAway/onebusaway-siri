package org.onebusaway.siri.serialization;

import org.onebusaway.siri.model.ServiceRequestContext;
import org.onebusaway.siri.model.VehicleMonitoringDetailLevel;
import org.onebusaway.siri.model.VehicleMonitoringRequest;

import com.thoughtworks.xstream.XStream;

public class SiriSerializer {

  private XStream stream;

  SiriSerializer() {
    broken!
    stream = new XStream();
    stream.processAnnotations(VehicleMonitoringRequest.class);
    stream.processAnnotations(VehicleMonitoringDetailLevel.class);
    stream.processAnnotations(ServiceRequestContext.class);
  }

  String serialize(Object o) {
    return stream.toXML(o);
  }

  Object deserialize(String s) {
    return stream.fromXML(s);
  }
}
