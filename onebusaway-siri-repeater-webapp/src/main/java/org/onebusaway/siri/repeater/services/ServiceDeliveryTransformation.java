package org.onebusaway.siri.repeater.services;

import uk.org.siri.siri.ServiceDelivery;

public interface ServiceDeliveryTransformation {
  public ServiceDelivery transform(ServiceDelivery delivery);
}
