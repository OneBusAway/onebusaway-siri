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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.onebusaway.siri.OneBusAwayVehicleActivity;
import org.onebusaway.siri.core.SiriTypeFactory;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

public class LayoverFilterTest {

  private LayoverFilter _filter;

  private LayoverService _layoverService;

  @Before
  public void setUp() {
    _filter = new LayoverFilter();
    _layoverService = Mockito.mock(LayoverService.class);
    _filter.setLayoverService(_layoverService);
  }

  @Test
  public void testInLayover() throws JAXBException {
    ServiceDelivery delivery = constructVehicleActivity();
    VehicleMonitoringDeliveryStructure vm = delivery.getVehicleMonitoringDelivery().get(0);
    VehicleActivityStructure activity = vm.getVehicleActivity().get(0);

    Mockito.when(_layoverService.isVehiclePausedAtLayoverLocation("123")).thenReturn(
        true);

    AbstractServiceDeliveryStructure filtered = _filter.filter(delivery, vm);

    Mockito.verify(_layoverService).updateVehicle(activity);
    assertSame(vm, filtered);

    OneBusAwayVehicleActivity extension = (OneBusAwayVehicleActivity) activity.getExtensions().getAny();
    assertTrue(extension.isLayover());
  }
  
  @Test
  public void testNoLayover() throws JAXBException {
    ServiceDelivery delivery = constructVehicleActivity();
    VehicleMonitoringDeliveryStructure vm = delivery.getVehicleMonitoringDelivery().get(0);
    VehicleActivityStructure activity = vm.getVehicleActivity().get(0);

    Mockito.when(_layoverService.isVehiclePausedAtLayoverLocation("123")).thenReturn(
        false);

    AbstractServiceDeliveryStructure filtered = _filter.filter(delivery, vm);

    Mockito.verify(_layoverService).updateVehicle(activity);
    assertSame(vm, filtered);
    assertNull(activity.getExtensions());
  }
  
  private ServiceDelivery constructVehicleActivity() {
    MonitoredVehicleJourney mvj = new MonitoredVehicleJourney();
    mvj.setVehicleRef(SiriTypeFactory.vehicleRef("123"));
    mvj.setMonitored(true);

    VehicleActivityStructure activity = new VehicleActivityStructure();
    activity.setMonitoredVehicleJourney(mvj);

    VehicleMonitoringDeliveryStructure vm = new VehicleMonitoringDeliveryStructure();
    vm.getVehicleActivity().add(activity);

    ServiceDelivery delivery = new ServiceDelivery();
    delivery.getVehicleMonitoringDelivery().add(vm);
    return delivery;
  }
}
