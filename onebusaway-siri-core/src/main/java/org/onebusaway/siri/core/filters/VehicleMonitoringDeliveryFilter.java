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

import java.util.List;

import org.onebusaway.siri.core.SiriLibrary;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

class VehicleMonitoringDeliveryFilter implements
    SiriModuleDeliveryFilter {

  private String _directionRef;

  private String _lineRef;

  private String _vehicleRef;

  private String _vehicleMonitoringRef;

  private int _maximumVehicles = 0;

  public void setDirectionRef(String directionRef) {
    _directionRef = directionRef;
  }

  public void setLineRef(String lineRef) {
    _lineRef = lineRef;
  }

  public void setVehicleRef(String vehicleRef) {
    _vehicleRef = vehicleRef;
  }

  /**
   * The vehicle monitoring ref is a grouping mechanism that can be used to
   * specify a tag value for a set of vehicles in a vehicle monitoring result,
   * which can be keyed off of for filtering.
   * 
   * @param vehicleMonitoringRef
   */
  public void setVehicleMonitoringRef(String vehicleMonitoringRef) {
    _vehicleMonitoringRef = vehicleMonitoringRef;
  }

  public void setMaximumVehicles(int maximumVehicles) {
    _maximumVehicles = maximumVehicles;
  }

  /****
   * {@link SiriModuleDeliveryFilter} Interface
   ****/

  @Override
  public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
      AbstractServiceDeliveryStructure moduleDelivery) {

    VehicleMonitoringDeliveryStructure vm = (VehicleMonitoringDeliveryStructure) moduleDelivery;

    List<VehicleActivityStructure> vasFiltered = vm.getVehicleActivity();

    if (_vehicleMonitoringRef != null) {
      vasFiltered = SiriLibrary.grep(vasFiltered, "vehicleMonitoringRef.value",
          _vehicleMonitoringRef);
    }

    if (_directionRef != null) {
      vasFiltered = SiriLibrary.grep(vasFiltered,
          "monitoredVehicleJourney.directionRef.value", _directionRef);
    }

    if (_lineRef != null) {
      vasFiltered = SiriLibrary.grep(vasFiltered,
          "monitoredVehicleJourney.lineRef.value", _lineRef);
    }

    if (_vehicleRef != null) {
      vasFiltered = SiriLibrary.grep(vasFiltered,
          "monitoredVehicleJourney.vehicleRef.value", _vehicleRef);
    }

    if (_maximumVehicles > 0 && vasFiltered.size() > _maximumVehicles) {
      while (vasFiltered.size() > _maximumVehicles)
        vasFiltered.remove(vasFiltered.size() - 1);
    }

    if (vasFiltered.isEmpty())
      return null;

    List<VehicleActivityStructure> vasOriginal = vm.getVehicleActivity();

    if (vasFiltered.size() < vasOriginal.size())
      SiriLibrary.copyList(vasFiltered, vasOriginal);

    return vm;
  }
}
