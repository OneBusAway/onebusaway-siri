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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.onebusaway.siri.core.SiriTypeFactory;

import uk.org.siri.siri.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri.LocationStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;

public class VehicleLocationTest {

  private static final String LINE_REF = "10";

  private static final String VEHICLE_REF = "1234";

  private PausedLocationListener _locationListener;

  @Before
  public void before() {
    _locationListener = Mockito.mock(PausedLocationListener.class);
  }

  @Test
  public void testIsStale() {
    VehicleLocation location = new VehicleLocation(VEHICLE_REF);
    assertTrue(location.isStale(0));

    location.update(new Date(0), mvj("t0", 47.668523, -122.290170), null);
    assertFalse(location.isStale(0));
    assertTrue(location.isStale(11 * 60 * 1000));
  }

  @Test
  public void testUpdate() {
    VehicleLocation location = new VehicleLocation(VEHICLE_REF);
    assertFalse(location.isPaused());

    location.update(mins(0), mvj("t0", 47.668523, -122.290170),
        _locationListener);
    assertFalse(location.isPaused());
    assertEquals(LINE_REF, location.getLineRef());
    assertEquals(mins(0), location.getLastLocationUpdateTime());

    // Not quite past our paused time threshold.
    location.update(mins(4), mvj("t0", 47.668523, -122.290170),
        _locationListener);
    assertFalse(location.isPaused());
    assertEquals(mins(0), location.getLastLocationUpdateTime());

    // Finally passed our "paused" time threshold.
    location.update(mins(6), mvj("t0", 47.668523, -122.290170),
        _locationListener);
    assertTrue(location.isPaused());

    // Move slightly but still close enough to original location.
    location.update(mins(7), mvj("t0", 47.668524, -122.290163),
        _locationListener);
    assertTrue(location.isPaused());
    CoordinatePoint centroid = location.getLocationCentroid();
    assertEquals(47.66852325, centroid.getLat(), 0.0000001);
    assertEquals(-122.2901682, centroid.getLng(), 0.000001);

    // Movement away from paused location should trigger a notification.
    location.update(mins(8), mvj("t0", 47.668519, -122.289948),
        _locationListener);
    assertFalse(location.isPaused());
    assertEquals(mins(8), location.getLastLocationUpdateTime());

    ArgumentCaptor<PausedLocation> captor = ArgumentCaptor.forClass(PausedLocation.class);
    Mockito.verify(_locationListener).handlePausedLocation(captor.capture());

    PausedLocation paused = captor.getValue();
    assertEquals(LINE_REF, paused.getLineRef());
    assertEquals(VEHICLE_REF, paused.getVehicleRef());
    assertEquals(mins(0), paused.getStartTime());
    assertEquals(mins(7), paused.getEndTime());
    assertEquals(0, paused.getUpdatesSinceTripChange());
    centroid = paused.getLocation();
    assertEquals(47.66852325, centroid.getLat(), 0.0000001);
    assertEquals(-122.2901682, centroid.getLng(), 0.000001);
  }

  @Test
  public void testUpdateWithDelayedTripUpdate() {
    VehicleLocation location = new VehicleLocation(VEHICLE_REF);

    // Initial location.
    location.update(mins(1), mvj("t0", 47.668519, -122.289948),
        _locationListener);

    // Trip change
    location.update(mins(2), mvj("t1", 47.668519, -122.289948),
        _locationListener);
    
    // A few updates until we hit our layover.
    location.update(mins(3), mvj("t1", 47.668534, -122.290060),
        _locationListener);
    location.update(mins(4), mvj("t1", 47.668534, -122.290060),
        _locationListener);

    // Move.
    location.update(mins(6), mvj("t1", 47.668523, -122.290170),
        _locationListener);

    // Pause at previous location.
    location.update(mins(12), mvj("t1", 47.668523, -122.290170),
        _locationListener);
    assertTrue(location.isPaused());

    // Movement away from paused location should not trigger a notification. We
    // need to see a trip change first.
    location.update(mins(13), mvj("t1", 47.668519, -122.289948),
        _locationListener);
    Mockito.verify(_locationListener, Mockito.times(0)).handlePausedLocation(
        null);
    
    // Trigger a trip change. This should trigger notification of the pending
    // paused location.
    location.update(mins(15), mvj("t0", 47.668519, -122.289948),
        _locationListener);

    ArgumentCaptor<PausedLocation> captor = ArgumentCaptor.forClass(PausedLocation.class);
    Mockito.verify(_locationListener).handlePausedLocation(captor.capture());

    PausedLocation paused = captor.getValue();
    assertEquals(mins(6), paused.getStartTime());
    assertEquals(mins(12), paused.getEndTime());
    assertEquals(2, paused.getUpdatesSinceTripChange());
  }
  
  @Test
  public void testUpdateWithDelayedTripUpdate_FirstTripChangeCloser() {
    VehicleLocation location = new VehicleLocation(VEHICLE_REF);

    // Initial location.
    location.update(mins(1), mvj("t0", 47.668519, -122.289948),
        _locationListener);

    // Trip change
    location.update(mins(2), mvj("t1", 47.668519, -122.289948),
        _locationListener);

    // Move.
    location.update(mins(3), mvj("t1", 47.668523, -122.290170),
        _locationListener);

    // Pause at previous location.
    location.update(mins(10), mvj("t1", 47.668523, -122.290170),
        _locationListener);
    assertTrue(location.isPaused());

    // Movement away from paused location should not trigger a notification. We
    // need to see a trip change first.
    location.update(mins(13), mvj("t1", 47.668519, -122.289948),
        _locationListener);
    Mockito.verify(_locationListener, Mockito.times(0)).handlePausedLocation(
        null);
    
    location.update(mins(14), mvj("t1", 47.668519, -122.289948),
        _locationListener);

    // Trigger a trip change. This should trigger notification of the pending
    // paused location.
    location.update(mins(15), mvj("t0", 47.668519, -122.289948),
        _locationListener);

    ArgumentCaptor<PausedLocation> captor = ArgumentCaptor.forClass(PausedLocation.class);
    Mockito.verify(_locationListener).handlePausedLocation(captor.capture());

    PausedLocation paused = captor.getValue();
    assertEquals(mins(3), paused.getStartTime());
    assertEquals(mins(10), paused.getEndTime());
    // Note that the earlier trip change is used.
    assertEquals(1, paused.getUpdatesSinceTripChange());
  }

  private Date mins(int minutes) {
    return new Date(minutes * 60 * 1000);
  }

  private MonitoredVehicleJourney mvj(String tripRef, double lat, double lng) {
    MonitoredVehicleJourney mvj = new MonitoredVehicleJourney();

    if (tripRef != null) {
      FramedVehicleJourneyRefStructure fvjRef = new FramedVehicleJourneyRefStructure();
      fvjRef.setDatedVehicleJourneyRef(tripRef);
      mvj.setFramedVehicleJourneyRef(fvjRef);
    }

    LocationStructure location = new LocationStructure();
    location.setLatitude(BigDecimal.valueOf(lat));
    location.setLongitude(BigDecimal.valueOf(lng));
    mvj.setVehicleLocation(location);

    mvj.setLineRef(SiriTypeFactory.lineRef(LINE_REF));
    mvj.setVehicleRef(SiriTypeFactory.vehicleRef(VEHICLE_REF));
    return mvj;
  }
}
