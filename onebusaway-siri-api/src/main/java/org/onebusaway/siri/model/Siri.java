package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

/** root element for all siri requests/responses */

@XStreamAlias("Siri")
public class Siri {
  @XStreamAsAttribute
  String version = "1.3";

  /*
   * a hack to work around XStream's lack of namespace support.
   */
  @XStreamAsAttribute
  String xmlns = "http://www.siri.org.uk/siri";

  public ServiceRequest ServiceRequest;
  public ServiceDelivery ServiceDelivery;
}
