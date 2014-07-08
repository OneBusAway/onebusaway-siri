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

import java.util.Date;

import org.junit.Test;

public class LayoverLocationTest {
  @Test
  public void testIsSignificant() {
    LayoverLocation location = new LayoverLocation(
        point(47.668523, -122.290170), mins(0), 1);
    assertFalse(location.isSignificant());
    location.update(point(47.668523, -122.290170), mins(1));
    assertFalse(location.isSignificant());
    location.update(point(47.668523, -122.290170), mins(3));
    assertTrue(location.isSignificant());
  }

  @Test
  public void testIsStale() {
    LayoverLocation location = new LayoverLocation(
        point(47.668523, -122.290170), mins(10), 1);
    assertFalse(location.isStale(mins(7 * 24 * 60).getTime()));
    assertTrue(location.isStale(mins(7 * 24 * 60 + 15).getTime()));
  }

  @Test
  public void testIncludesLocation() {
    LayoverLocation location = new LayoverLocation(
        point(47.668523, -122.290170), mins(10), 1);
    assertTrue(location.includesLocation(point(47.668523, -122.290170)));
    assertTrue(location.includesLocation(point(47.668538, -122.288475)));
    assertFalse(location.includesLocation(point(47.668509, -122.287112)));
    assertFalse(location.includesLocation(point(47.668538, -122.284827)));
  }

  @Test
  public void testGetCentroid() {
    LayoverLocation location = new LayoverLocation(
        point(47.668523, -122.290170), mins(0), 1);
    location.update(point(47.668527, -122.290174), mins(1));
    CoordinatePoint p = location.getCentroid();
    assertEquals(47.668525, p.getLat(), 0.000001);
    assertEquals(-122.290172, p.getLng(), 0.000001);
  }

  private CoordinatePoint point(double lat, double lng) {
    return new CoordinatePoint(lat, lng);
  }

  private Date mins(int minutes) {
    return new Date(minutes * 60 * 1000);
  }
}
