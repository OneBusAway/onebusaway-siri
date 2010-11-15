package org.onebusaway.siri.repeater.impl.transform;

import java.util.List;

import org.onebusaway.siri.repeater.services.ServiceDeliveryTransformation;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

public class VehicleMonitoringFilter implements ServiceDeliveryTransformation {

  @Override
  public ServiceDelivery transform(ServiceDelivery delivery) {

    List<VehicleMonitoringDeliveryStructure> vms = delivery.getVehicleMonitoringDelivery();
    if (vms.isEmpty())
      return null;

    return delivery;
  }
}
