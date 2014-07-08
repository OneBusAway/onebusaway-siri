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

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.onebusaway.siri.core.SiriTypeFactory;

import uk.org.siri.siri.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri.LocationStructure;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;


public class LayoverServiceImplTest {

  private LayoverServiceImpl _service = new LayoverServiceImpl();
  
  @Test
  public void testService() {
    _service.updateVehicle(activity(0, 47.668509, -122.290192, "t0"));
    assertFalse(_service.isVehiclePaused("1234"));
    List<LayoverLocation> layovers = _service.getLayoverLocationsForLineRef("10");
    assertEquals(0, layovers.size());

    _service.updateVehicle(activity(6, 47.668509, -122.290192, "t1"));
    assertTrue(_service.isVehiclePaused("1234"));
    assertFalse(_service.isVehiclePausedAtLayoverLocation("1234"));
    layovers = _service.getLayoverLocationsForLineRef("10");
    assertEquals(0, layovers.size());

    _service.updateVehicle(activity(7, 47.668514, -122.289640, "t1"));
    assertFalse(_service.isVehiclePaused("1234"));
    layovers = _service.getLayoverLocationsForLineRef("10");
    assertEquals(1, layovers.size());
    LayoverLocation layover = layovers.get(0);
    assertFalse(layover.isSignificant());

    // We need to loop through a few more times to make the layover location
    // significant.
    for (int i = 0; i < 3; ++i) {
      int tOffset = 10 * (i + 1);
      _service.updateVehicle(activity(tOffset + 0, 47.668509, -122.290192, "t0"));
      _service.updateVehicle(activity(tOffset + 6, 47.668509, -122.290192, "t1"));
      _service.updateVehicle(activity(tOffset + 7, 47.668514, -122.289640, "t1"));
    }

    layovers = _service.getLayoverLocationsForLineRef("10");
    assertEquals(1, layovers.size());
    layover = layovers.get(0);
    assertTrue(layover.isSignificant());

    _service.updateVehicle(activity(40, 47.668509, -122.290192, "t1"));
    _service.updateVehicle(activity(47, 47.668509, -122.290192, "t1"));
    assertTrue(_service.isVehiclePausedAtLayoverLocation("1234"));
  }

  @Test
  public void testLoadAndSaveLayoverLocations() throws IOException {
    String line = "10,47.668509,-122.290192,5\n";
    File tmpFile = File.createTempFile(
        LayoverServiceImplTest.class.getName() + "-", ".txt");
    tmpFile.deleteOnExit();
    saveStringToFile(line, tmpFile);

    _service.loadLayoverLocations(tmpFile);
    _service.setDataPath(tmpFile);

    List<LayoverLocation> locations = _service.getLayoverLocationsForLineRef("10");
    assertEquals(1, locations.size());
    LayoverLocation location = locations.get(0);
    CoordinatePoint p = location.getCentroid();
    assertEquals(47.668509, p.getLat(), 0.000001);
    assertEquals(-122.290192, p.getLng(), 0.000001);
    assertEquals(5, location.getSamples());

    tmpFile.delete();
    _service.saveLayoverLocations();
    
    String newLine = readStringFromFile(tmpFile);
    assertEquals(line, newLine);
  }

  private VehicleActivityStructure activity(int time_mins, double lat,
      double lng, String tripRef) {

    VehicleActivityStructure vehicleActivity = new VehicleActivityStructure();

    vehicleActivity.setRecordedAtTime(new Date(time_mins * 60 * 1000));

    MonitoredVehicleJourney mvj = new MonitoredVehicleJourney();
    vehicleActivity.setMonitoredVehicleJourney(mvj);

    LocationStructure location = new LocationStructure();
    location.setLatitude(BigDecimal.valueOf(lat));
    location.setLongitude(BigDecimal.valueOf(lng));
    mvj.setVehicleLocation(location);

    FramedVehicleJourneyRefStructure fvj = new FramedVehicleJourneyRefStructure();
    fvj.setDatedVehicleJourneyRef(tripRef);
    mvj.setFramedVehicleJourneyRef(fvj);
    
    mvj.setLineRef(SiriTypeFactory.lineRef("10"));
    mvj.setVehicleRef(SiriTypeFactory.vehicleRef("1234"));

    return vehicleActivity;
  }

  private void saveStringToFile(String content, File file) throws IOException {
    FileWriter writer = new FileWriter(file);
    writer.write(content);
    writer.close();
  }

  private String readStringFromFile(File file) throws IOException {
    StringBuilder b = new StringBuilder();
    String line = null;
    BufferedReader reader = new BufferedReader(new FileReader(file));
    while ((line = reader.readLine()) != null) {
      b.append(line).append("\n");
    }
    reader.close();
    return b.toString();
  }

}
