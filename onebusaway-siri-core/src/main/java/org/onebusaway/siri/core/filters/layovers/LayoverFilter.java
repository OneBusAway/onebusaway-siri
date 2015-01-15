/**
 * Copyright (C) 2014 Google, Inc.
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
package org.onebusaway.siri.core.filters.layovers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ExtensionsStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

public class LayoverFilter implements SiriModuleDeliveryFilter {

  private static final Logger _log = LoggerFactory.getLogger(LayoverServiceImpl.class);

  private LayoverService _layoverService = new LayoverServiceImpl();

  public void setDataPath(String path) {
    try {
      _layoverService.loadLayoverLocations(new File(path));
    } catch (IOException ex) {
      _log.error("Error loading layover data from path: " + path, ex);
    }
  }
  
  public void setLayoverService(LayoverService layoverService) {
    _layoverService = layoverService;
  }
  
  @Override
  public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
      AbstractServiceDeliveryStructure moduleDelivery) {
    if (!(moduleDelivery instanceof VehicleMonitoringDeliveryStructure)) {
      return moduleDelivery;
    }
    VehicleMonitoringDeliveryStructure vm = (VehicleMonitoringDeliveryStructure) moduleDelivery;
    for (VehicleActivityStructure activity : vm.getVehicleActivity()) {
      MonitoredVehicleJourney mvj = activity.getMonitoredVehicleJourney();
      if (!hasMonitoringError(mvj)) {
        _layoverService.updateVehicle(activity);
      }
      
      if (mvj.getVehicleRef() != null) {
        String vehicleRef = mvj.getVehicleRef().getValue();
        if (_layoverService.isVehiclePausedAtLayoverLocation(vehicleRef)) {
          OneBusAwayVehicleActivity vaExtension = new OneBusAwayVehicleActivity();
          vaExtension.setLayover(true);
          ExtensionsStructure extensions = new ExtensionsStructure();
          extensions.setAny(vaExtension);
          activity.setExtensions(extensions);
        }
      }
    }
    return moduleDelivery;
  }

  private boolean hasMonitoringError(MonitoredVehicleJourney mvj) {
    if (mvj.isMonitored() != null && mvj.isMonitored()) {
      return false;
    }
    List<String> errors = mvj.getMonitoringError();
    return errors != null && !errors.isEmpty();
  }

}
