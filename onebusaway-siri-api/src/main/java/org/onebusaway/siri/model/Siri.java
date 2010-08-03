package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/** root element for all siri requests/responses */

@XStreamAlias("Siri")
public class Siri {
  public ServiceRequest ServiceRequest;
  public ServiceDelivery ServiceDelivery;
}
