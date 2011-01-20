package org.onebusaway.siri.repeater;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri.VehicleRefStructure;

public class TestFilter implements SiriModuleDeliveryFilter {

  private String _vehicleId = "2305";

  public void setVehicleId(String vehicleId) {
    _vehicleId = vehicleId;
  }

  @Override
  public AbstractServiceDeliveryStructure filter(
      AbstractServiceDeliveryStructure delivery) {

    VehicleMonitoringDeliveryStructure vm = (VehicleMonitoringDeliveryStructure) delivery;

    List<VehicleActivityStructure> vas = vm.getVehicleActivity();
    List<VehicleActivityStructure> vasFiltered = new ArrayList<VehicleActivityStructure>();

    for (VehicleActivityStructure va : vas) {

      MonitoredVehicleJourney mvj = va.getMonitoredVehicleJourney();

      if (mvj == null)
        continue;

      VehicleRefStructure vehicleRef = mvj.getVehicleRef();

      if (vehicleRef == null || vehicleRef.getValue() == null)
        continue;

      String vid = vehicleRef.getValue();

      if (!vid.equals(_vehicleId))
        continue;

      vasFiltered.add(va);
    }

    if (vasFiltered.isEmpty())
      return null;

    if (vasFiltered.size() < vas.size())
      SiriLibrary.copyList(vasFiltered, vas);

    return vm;
  }
}
