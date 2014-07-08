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

import java.io.Serializable;
import java.util.Date;

class LayoverLocation implements Serializable {

  private static final long serialVersionUID = 1L;
  
  private static final int NUM_SAMPLES_FOR_SIGNIFICANCE = 3;

  /**
   * Paused locations within the specified distance will be considered included
   * in this layover location.
   */
  private static final double MAX_LAYOVER_CLUSTER_DISTANCE_M = 150;

  /**
   * We only get rid of a layover location if we haven't received an update in a
   * week.
   */
  private static final long MAX_TIME_BETWEEN_UPDATES_MS = 7 * 24 * 60 * 60
      * 1000;

  /**
   * The layover loation's center.
   */
  private CoordinatePoint _centroid;

  /**
   * The time when the most recent location sample was added to the layover
   * location.
   */
  private Date _lastUpdateTime;

  /**
   * The number of paused-location samples that make up this layover location.
   */
  private int _samples;

  public LayoverLocation(PausedLocation pausedLocation) {
    this(pausedLocation.getLocation(), pausedLocation.getEndTime(), 1);
  }

  public LayoverLocation(CoordinatePoint centroid, Date lastUpdateTime,
      int samples) {
    _centroid = centroid;
    _lastUpdateTime = lastUpdateTime;
    _samples = samples;
  }

  public CoordinatePoint getCentroid() {
    return _centroid;
  }

  public int getSamples() {
    return _samples;
  }

  /**
   * @return true if the layover location has enough supporting data to safely
   *         be used as a layover location.
   */
  public boolean isSignificant() {
    return _samples >= NUM_SAMPLES_FOR_SIGNIFICANCE;
  }

  public boolean isStale(long t) {
    return t - _lastUpdateTime.getTime() > MAX_TIME_BETWEEN_UPDATES_MS;
  }

  public boolean includesLocation(CoordinatePoint location) {
    return _centroid.getDistance(location) <= MAX_LAYOVER_CLUSTER_DISTANCE_M;
  }

  public void update(CoordinatePoint location, Date updateTime) {
    double lat = runningAverage(_centroid.getLat(), location.getLat());
    double lng = runningAverage(_centroid.getLng(), location.getLng());
    _centroid = new CoordinatePoint(lat, lng);
    _samples = _samples + 1;
    _lastUpdateTime = updateTime;
  }

  private double runningAverage(double oldValue, double newValue) {
    return (oldValue * _samples + newValue) / (_samples + 1);
  }
}