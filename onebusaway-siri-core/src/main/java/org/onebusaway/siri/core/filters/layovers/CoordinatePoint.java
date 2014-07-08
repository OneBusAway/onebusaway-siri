/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;

import java.io.Serializable;

public final class CoordinatePoint implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final double RADIUS_OF_EARTH_IN_KM = 6371.01;

  private final double _lat;

  private final double _lng;

  public CoordinatePoint(double lat, double lng) {
    _lat = lat;
    _lng = lng;
  }

  public double getLat() {
    return _lat;
  }

  public double getLng() {
    return _lng;
  }

  public double getDistance(CoordinatePoint p) {
    return distance(_lat, _lng, p._lat, p._lng, RADIUS_OF_EARTH_IN_KM * 1000);
  }

  public static final double distance(double lat1, double lon1, double lat2,
      double lon2, double radius) {

    // http://en.wikipedia.org/wiki/Great-circle_distance
    lat1 = toRadians(lat1); // Theta-s
    lon1 = toRadians(lon1); // Lambda-s
    lat2 = toRadians(lat2); // Theta-f
    lon2 = toRadians(lon2); // Lambda-f

    double deltaLon = lon2 - lon1;

    double y = sqrt(p2(cos(lat2) * sin(deltaLon))
        + p2(cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)));
    double x = sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(deltaLon);

    return radius * atan2(y, x);
  }

  private static final double p2(double a) {
    return a * a;
  }

  @Override
  public String toString() {
    return _lat + " " + _lng;
  }

  @Override
  public int hashCode() {
    return new Double(_lng).hashCode() + new Double(_lng).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this)
      return true;
    if (o == null || !(o instanceof CoordinatePoint))
      return false;
    CoordinatePoint p = (CoordinatePoint) o;
    return this._lat == p._lat && this._lng == p._lng;
  }
}