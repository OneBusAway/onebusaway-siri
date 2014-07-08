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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;

/**
 * Service for automatic detection of transit vehicle layover locations (places
 * where vehicles wait between trips). Some AVL systems (eg. Init AVL) produce
 * unexpected delay values when a vehicle is sitting in layover with its AVL
 * equipment still running. It is useful to be able to detect these situations
 * such that we might take action.
 * 
 * @author bdferris
 */
class LayoverServiceImpl implements LayoverService {

  private static Logger _log = LoggerFactory.getLogger(LayoverServiceImpl.class);

  private static final long MAX_TIME_BETWEEN_HOUSEKEEPING_MS = 60 * 60 * 1000;

  private static final long MAX_UPDATES_SINCE_TRIP_CHANGE_FOR_LAYOVER_LOCATION = 2;

  private Map<String, VehicleLocation> _vehiclesById = new HashMap<String, VehicleLocation>();

  /**
   * We group layover locations by LineRef since vehicles belonging to the same
   * line tend to have specific layover locations. More importantly, since most
   * lines have just one or two layover locations, it reduces the number of
   * locations we have to check when determining if a particular paused vehicle
   * is a layover location without having to resort to fancier techniques (eg.
   * geo-spatial index).
   */
  private Map<String, List<LayoverLocation>> _layoverLocationsByLineRef = new HashMap<String, List<LayoverLocation>>();

  /**
   * Time at which we last performed house-keeping operations.
   */
  private long _lastHousekeepingTime = -1;

  private PausedLocationListener _pausedLocationListener = new PausedLocationListenerImpl();

  /**
   * If specified, layover locations will be read from this path on service
   * startup and periodically written to this path during service execution.
   * Since it can take time to detect layover locations from scratch, a data
   * file allows the service to initialize with a known set of layover locations
   * on startup.
   */
  private File _dataPath;

  /**
   * @param path the file where layover locations will be read and written
   */
  public void setDataPath(File path) {
    _dataPath = path;
  }

  /**
   * Takes real-time location data from the specified vehicle and uses it to
   * update the state of the specified vehicle and potentially update layover
   * locations if the vehicle has been paused in the same location for a
   * sufficient amount of time.
   * 
   * @param activity
   */
  @Override
  public void updateVehicle(VehicleActivityStructure activity) {
    Date recordedAt = activity.getRecordedAtTime();
    MonitoredVehicleJourney mvj = activity.getMonitoredVehicleJourney();
    if (mvj.getVehicleRef() == null) {
      return;
    }
    String vehicleRef = mvj.getVehicleRef().getValue();
    VehicleLocation vehicle = _vehiclesById.get(vehicleRef);
    if (vehicle == null) {
      vehicle = new VehicleLocation(vehicleRef);
      _vehiclesById.put(vehicleRef, vehicle);
    }
    vehicle.update(recordedAt, mvj, _pausedLocationListener);
    performHousekeepingIfNeeded(recordedAt);
  }

  public boolean isVehiclePaused(String vehicleRef) {
    VehicleLocation vehicle = _vehiclesById.get(vehicleRef);
    if (vehicle == null) {
      return false;
    }
    return vehicle.isPaused();
  }

  @Override
  public boolean isVehiclePausedAtLayoverLocation(String vehicleRef) {
    VehicleLocation vehicle = _vehiclesById.get(vehicleRef);
    if (vehicle == null) {
      return false;
    }
    if (!vehicle.isPaused()) {
      return false;
    }
    String lineRef = vehicle.getLineRef();
    List<LayoverLocation> locations = _layoverLocationsByLineRef.get(lineRef);
    if (locations == null) {
      return false;
    }
    for (LayoverLocation location : locations) {
      if (location.isSignificant()
          && location.includesLocation(vehicle.getLocation())) {
        _log.debug("vehicle_paused_in_layover={}", vehicleRef);
        return true;
      }
    }
    return false;
  }

  private void processPotentialLayoverLocation(PausedLocation pausedLocation) {
    if (pausedLocation.getUpdatesSinceTripChange() > MAX_UPDATES_SINCE_TRIP_CHANGE_FOR_LAYOVER_LOCATION) {
      return;
    }
    String lineRef = pausedLocation.getLineRef();
    List<LayoverLocation> locations = getOrCreateLayoverLocationsForLineRef(lineRef);
    boolean updatedExistingCluster = false;
    for (LayoverLocation layoverLocation : locations) {
      if (layoverLocation.includesLocation(pausedLocation.getLocation())) {
        boolean wasSignificant = layoverLocation.isSignificant();
        layoverLocation.update(pausedLocation.getLocation(),
            pausedLocation.getEndTime());
        if (!wasSignificant && layoverLocation.isSignificant()) {
          _log.debug("significant_layover_location="
              + layoverLocation.getCentroid() + " " + lineRef);
        }
        updatedExistingCluster = true;
        break;
      }
    }
    if (!updatedExistingCluster) {
      _log.debug("new_layover_location=" + pausedLocation + " " + lineRef);
      locations.add(new LayoverLocation(pausedLocation));
    }
  }

  private List<LayoverLocation> getOrCreateLayoverLocationsForLineRef(
      String lineRef) {
    List<LayoverLocation> locations = _layoverLocationsByLineRef.get(lineRef);
    if (locations == null) {
      locations = new ArrayList<LayoverLocation>();
      _layoverLocationsByLineRef.put(lineRef, locations);
    }
    return locations;
  }

  private void performHousekeepingIfNeeded(Date currentTime) {
    long t = currentTime.getTime();
    if (t - _lastHousekeepingTime < MAX_TIME_BETWEEN_HOUSEKEEPING_MS) {
      return;
    }
    _lastHousekeepingTime = t;
    pruneStaleRecords(t);
    try {
      saveLayoverLocations();
    } catch (IOException ex) {
      _log.error("error saving layover locations", ex);
    }
  }

  private void pruneStaleRecords(long t) {
    for (Iterator<VehicleLocation> it = _vehiclesById.values().iterator(); it.hasNext();) {
      VehicleLocation vehicle = it.next();
      if (vehicle.isStale(t)) {
        it.remove();
      }
    }
    for (List<LayoverLocation> locations : _layoverLocationsByLineRef.values()) {
      for (Iterator<LayoverLocation> it = locations.iterator(); it.hasNext();) {
        LayoverLocation location = it.next();
        if (location.isStale(t)) {
          it.remove();
        }
      }
    }
  }

  @Override
  public void loadLayoverLocations(File dataPath) throws IOException {
    _dataPath = dataPath;
    if (!_dataPath.exists()) {
      return;
    }
    BufferedReader reader = new BufferedReader(new FileReader(_dataPath));
    String line = null;
    Date now = new Date();
    try {
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split(",");
        if (tokens.length < 4) {
          throw new IllegalStateException(
              "Invalid serialized layover location=" + line);
        }
        String lineRef = tokens[0];
        double lat = Double.parseDouble(tokens[1]);
        double lng = Double.parseDouble(tokens[2]);
        CoordinatePoint location = new CoordinatePoint(lat, lng);
        int samples = Integer.parseInt(tokens[3]);
        LayoverLocation layoverLocation = new LayoverLocation(location, now,
            samples);
        List<LayoverLocation> locations = getOrCreateLayoverLocationsForLineRef(lineRef);
        locations.add(layoverLocation);
      }
    } finally {
      try {
        reader.close();
      } catch (IOException ex) {
        _log.error("error closing file", ex);
      }
    }
  }

  public void saveLayoverLocations() throws IOException {
    if (_dataPath == null) {
      return;
    }
    BufferedWriter writer = new BufferedWriter(new FileWriter(_dataPath));
    try {
      for (Map.Entry<String, List<LayoverLocation>> entry : _layoverLocationsByLineRef.entrySet()) {
        String lineRef = entry.getKey();
        for (LayoverLocation location : entry.getValue()) {
          CoordinatePoint p = location.getCentroid();
          StringBuilder b = new StringBuilder();
          b.append(lineRef).append(",");
          b.append(p.getLat()).append(",");
          b.append(p.getLng()).append(",");
          b.append(location.getSamples()).append("\n");
          writer.write(b.toString());;
        }
      }
    } finally {
      try {
        writer.close();
      } catch (IOException ex) {
        _log.error("error closing file", ex);
      }
    }
  }

  private class PausedLocationListenerImpl implements PausedLocationListener {
    @Override
    public void handlePausedLocation(PausedLocation location) {
      processPotentialLayoverLocation(location);
    }
  }

  // Visible for testing
  List<LayoverLocation> getLayoverLocationsForLineRef(String lineRef) {
    List<LayoverLocation> locations = _layoverLocationsByLineRef.get(lineRef);
    if (locations == null) {
      return Collections.emptyList();
    }
    return locations;
  }
}
