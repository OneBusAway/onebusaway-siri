/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.core.filters;

import java.math.BigInteger;

import org.onebusaway.siri.core.ESiriModuleType;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.DirectionRefStructure;
import uk.org.siri.siri.LineRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleMonitoringRefStructure;
import uk.org.siri.siri.VehicleMonitoringRequestStructure;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;
import uk.org.siri.siri.VehicleRefStructure;

public class ModuleDeliveryFilterFactory {
  private static final EmptyFilter _emptyFilter = new EmptyFilter();

  public SiriModuleDeliveryFilter createFilter(ESiriModuleType moduleType,
      AbstractSubscriptionStructure request) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        return createVehicleMonitoringFilter((VehicleMonitoringSubscriptionStructure) request);
      default:
        return _emptyFilter;
    }
  }

  /****
   * 
   ****/

  private SiriModuleDeliveryFilter createVehicleMonitoringFilter(
      VehicleMonitoringSubscriptionStructure subscription) {

    VehicleMonitoringDeliveryFilter filter = new VehicleMonitoringDeliveryFilter();

    VehicleMonitoringRequestStructure vmRequest = subscription.getVehicleMonitoringRequest();

    if (vmRequest != null) {
      DirectionRefStructure directionRef = vmRequest.getDirectionRef();
      if (directionRef != null && directionRef.getValue() != null)
        filter.setDirectionRef(directionRef.getValue());

      LineRefStructure lineRef = vmRequest.getLineRef();
      if (lineRef != null && lineRef.getValue() != null)
        filter.setLineRef(lineRef.getValue());

      BigInteger maxVehicles = vmRequest.getMaximumVehicles();
      if (maxVehicles != null && maxVehicles.intValue() > 0)
        filter.setMaximumVehicles(maxVehicles.intValue());

      VehicleMonitoringRefStructure vmRef = vmRequest.getVehicleMonitoringRef();
      if (vmRef != null && vmRef.getValue() != null) {
        filter.setVehicleMonitoringRef(vmRef.getValue());
      }

      VehicleRefStructure vehicleRef = vmRequest.getVehicleRef();
      if (vehicleRef != null && vehicleRef.getValue() != null)
        filter.setVehicleRef(vehicleRef.getValue());

    }

    return filter;
  }

  private static class EmptyFilter implements SiriModuleDeliveryFilter {

    @Override
    public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
        AbstractServiceDeliveryStructure moduleDelivery) {
      return moduleDelivery;
    }
  }
}
