package org.onebusaway.siri.jetty;

import java.util.ArrayList;
import java.util.List;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriServer;

import uk.org.siri.siri.BlockRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri.VehicleRefStructure;

public class SourceSimulatorMain {

  private static DatatypeFactory _dataTypeFactory;

  public static void main(String[] args) throws InterruptedException,
      DatatypeConfigurationException {

    _dataTypeFactory = DatatypeFactory.newInstance();

    SiriJettyServer server = new SiriJettyServer();

    List<ESiriModuleType> modules = new ArrayList<ESiriModuleType>();
    for (String arg : args)
      modules.add(ESiriModuleType.valueOf(arg));

    server.start();

    int index = 0;
    
    while (true) {

      ServiceDelivery delivery = new ServiceDelivery();

      for (ESiriModuleType module : modules) {
        switch (module) {
          case VEHICLE_MONITORING:
            addVehicleMonitoringData(delivery,index);
        }
      }

      server.publish(delivery);

      Thread.sleep(5000);
      index++;
    }
  }

  private static void addVehicleMonitoringData(ServiceDelivery delivery, int index) {

    List<VehicleMonitoringDeliveryStructure> vms = delivery.getVehicleMonitoringDelivery();

    VehicleMonitoringDeliveryStructure vm = new VehicleMonitoringDeliveryStructure();
    vms.add(vm);

    List<VehicleActivityStructure> vas = vm.getVehicleActivity();

    VehicleActivityStructure va = new VehicleActivityStructure();
    vas.add(va);

    MonitoredVehicleJourney mvj = new MonitoredVehicleJourney();
    va.setMonitoredVehicleJourney(mvj);

    String vid = index % 2 == 0 ? "100" : "101";
    VehicleRefStructure vehicleId = new VehicleRefStructure();
    vehicleId.setValue(vid);
    mvj.setVehicleRef(vehicleId);

    mvj.setDelay(_dataTypeFactory.newDuration(60 * 1000));

    BlockRefStructure block = new BlockRefStructure();
    block.setValue("blockId");
    mvj.setBlockRef(block);
  }
}
