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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri.LocationStructure;
import uk.org.siri.siri.VehicleActivityStructure.MonitoredVehicleJourney;

/**
 * Instance of a single transit vehicle.
 */
class VehicleLocation {

  private static Logger _log = LoggerFactory.getLogger(VehicleLocation.class);

  /**
   * When comparing vehicle location updates, if we haven't received an update
   * from the vehicle in the specified time range, we reset the state of the
   * vehicle.
   */
  private static final long MIN_UPDATE_TIME_MS = 10 * 60 * 1000;

  /**
   * When comparing vehicle location updates, the max distance a vehicle is
   * allowed to move before we no longer consider it "paused" at the starting
   * location.
   */
  private static final double MAX_PAUSED_LOCATION_DISTANCE_M = 10;

  /**
   * How long a transit vehicle must be paused at the same location for the
   * location to be considered as a potential layover location.
   */
  private static final long MIN_PAUSED_LOCATION_TIME_MS = 5 * 60 * 1000;

  private final String _vehicleRef;

  /**
   * The last time we received an update for this vehicle.
   */
  private Date _lastUpdateTime;

  private long _lastUpdateIndex = 0;

  /**
   * The vehicle's current location. Note that we might have received subsequent
   * location updates, but if they are within
   * {@link LayoverServiceImpl#MAX_PAUSED_LOCATION_DISTANCE_M} meters of the
   * previous point, then the vehicle is still considered paused and the
   * location is not updated. These location updates are instead stored in
   * {@link #_mergedLocations}.
   */
  private CoordinatePoint _location;

  /**
   * Time at which the current vehicle location was first noted.
   */
  private Date _lastLocationUpdateTime;

  private long _lastLocationUpdateIndex = 0;

  /**
   * All vehicle locations received since {@link #_lastLocationUpdateTime} that
   * were ignored because they were sufficiently close to the previous location
   * update.
   */
  private List<CoordinatePoint> _mergedLocations = new ArrayList<CoordinatePoint>();

  /**
   * Some unique identifier for the SIRI line / GTFS route that the vehicle is
   * currently serving.
   */
  private String _lineRef;

  /**
   * Index at which the current trip ref was first noted.
   */
  private long _tripRefUpdateIndex = -1;

  private String _tripRef;

  private List<PausedLocation> _pendingTripChange = new ArrayList<PausedLocation>();

  public VehicleLocation(String vehicleRef) {
    _vehicleRef = vehicleRef;
  }

  public String getVehicleRef() {
    return _vehicleRef;
  }

  public CoordinatePoint getLocation() {
    return _location;
  }

  public Date getLastLocationUpdateTime() {
    return _lastLocationUpdateTime;
  }

  public String getLineRef() {
    return _lineRef;
  }

  public boolean isStale(long currentTime) {
    return _lastUpdateTime == null
        || currentTime - _lastUpdateTime.getTime() > MIN_UPDATE_TIME_MS;
  }

  public boolean isPaused() {
    if (_lastLocationUpdateTime == null) {
      return false;
    }
    long duration = _lastUpdateTime.getTime()
        - _lastLocationUpdateTime.getTime();
    return duration > MIN_PAUSED_LOCATION_TIME_MS;
  }

  public void update(Date recordedAt, MonitoredVehicleJourney mvj,
      PausedLocationListener listener) {
    if (_lastUpdateTime != null && _lastUpdateTime.after(recordedAt)) {
      _log.warn("time out of order: old=" + _lastUpdateTime + " new="
          + recordedAt);
      return;
    }
    if (isStale(recordedAt.getTime())) {
      clear(listener);
    }
    long updateIndex = _lastUpdateIndex + 1;

    processTripRefChange(recordedAt, updateIndex, mvj, listener);

    LocationStructure location = mvj.getVehicleLocation();
    CoordinatePoint p = new CoordinatePoint(
        location.getLatitude().doubleValue(),
        location.getLongitude().doubleValue());
    if (!isClustered(p)) {
      processPausedLocationIfApplicable(listener);
      _lastLocationUpdateTime = recordedAt;
      _lastLocationUpdateIndex = updateIndex;
      _location = p;
      _mergedLocations.clear();
      _lineRef = mvj.getLineRef().getValue();
    }
    _mergedLocations.add(p);

    _lastUpdateTime = recordedAt;
    _lastUpdateIndex = updateIndex;

    if (_lastUpdateIndex < _lastLocationUpdateIndex
        || _lastUpdateIndex < _tripRefUpdateIndex) {
      throw new IllegalStateException();
    }
  }

  public CoordinatePoint getLocationCentroid() {
    double lats = 0;
    double lngs = 0;
    for (CoordinatePoint p : _mergedLocations) {
      lats += p.getLat();
      lngs += p.getLng();
    }
    double lat = lats / _mergedLocations.size();
    double lng = lngs / _mergedLocations.size();
    return new CoordinatePoint(lat, lng);
  }

  private boolean isClustered(CoordinatePoint p) {
    if (_location == null) {
      return false;
    }
    return p.getDistance(_location) <= MAX_PAUSED_LOCATION_DISTANCE_M;
  }

  private void clear(PausedLocationListener listener) {
    processPausedLocationIfApplicable(listener);
    _lastUpdateTime = null;
    _lastLocationUpdateTime = null;
    _location = null;
    _mergedLocations.clear();
    _lineRef = null;
  }

  private void processTripRefChange(Date recordedAt, long updateIndex,
      MonitoredVehicleJourney mvj, PausedLocationListener listener) {
    String tripRef = getTripRef(mvj);
    if (_tripRef != null && _tripRef.equals(tripRef)) {
      return;
    }

    _tripRefUpdateIndex = updateIndex;
    _tripRef = tripRef;
    for (PausedLocation paused : _pendingTripChange) {
      long updatesSinceTripChange = _tripRefUpdateIndex - paused.getEndIndex();
      if (updatesSinceTripChange < paused.getUpdatesSinceTripChange()) {
        paused.setUpdatesSinceTripChange(updatesSinceTripChange);
      }
      listener.handlePausedLocation(paused);
    }
    _pendingTripChange.clear();
  }

  private String getTripRef(MonitoredVehicleJourney mvj) {
    FramedVehicleJourneyRefStructure fvjRef = mvj.getFramedVehicleJourneyRef();
    if (fvjRef == null) {
      return null;
    }
    return fvjRef.getDatedVehicleJourneyRef();
  }

  private void processPausedLocationIfApplicable(PausedLocationListener listener) {
    if (!isPaused()) {
      return;
    }
    PausedLocation location = new PausedLocation();
    location.setLineRef(_lineRef);
    location.setVehicleRef(_vehicleRef);
    location.setLocation(getLocationCentroid());
    location.setStartTime(_lastLocationUpdateTime);
    location.setEndTime(_lastUpdateTime);
    location.setEndIndex(_lastUpdateIndex);
    if (_tripRefUpdateIndex != -1) {
      if (_lastLocationUpdateIndex <= _tripRefUpdateIndex
          && _tripRefUpdateIndex <= _lastUpdateIndex) {
        // The trip change occurred while the vehicle was paused.
        location.setUpdatesSinceTripChange(0);
      } else if (_tripRefUpdateIndex < _lastLocationUpdateIndex) {
        // The trip change occurred before the vehicle stopped moving.
        location.setUpdatesSinceTripChange(_lastLocationUpdateIndex
            - _tripRefUpdateIndex);
      } else {
        // The trip change occurred after the vehicle started moving again.
        location.setUpdatesSinceTripChange(_tripRefUpdateIndex
            - _lastUpdateIndex);
      }

      // If the vehicle saw a trip change while paused or after, we can go ahead
      // and send notify the paused location since no subsequent trip change
      // will change the stats of this paused location.
      if (location.getUpdatesSinceTripChange() == 0
          || _tripRefUpdateIndex > _lastLocationUpdateIndex) {
        listener.handlePausedLocation(location);
        return;
      }
    }

    _pendingTripChange.add(location);
  }
}