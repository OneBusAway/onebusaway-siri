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

import java.text.DateFormat;
import java.util.Date;

class PausedLocation {

  private static DateFormat _format = DateFormat.getDateTimeInstance(
      DateFormat.SHORT, DateFormat.SHORT);

  private String lineRef;
  private String vehicleRef;
  private CoordinatePoint location;
  private Date startTime;
  private Date endTime;
  private long endIndex;
  private long updatesSinceTripChange = -1;

  public String getLineRef() {
    return lineRef;
  }

  public void setLineRef(String lineRef) {
    this.lineRef = lineRef;
  }

  public String getVehicleRef() {
    return vehicleRef;
  }

  public void setVehicleRef(String vehicleRef) {
    this.vehicleRef = vehicleRef;
  }

  public CoordinatePoint getLocation() {
    return location;
  }

  public void setLocation(CoordinatePoint location) {
    this.location = location;
  }

  public Date getStartTime() {
    return startTime;
  }

  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }

  public Date getEndTime() {
    return endTime;
  }

  public void setEndTime(Date endTime) {
    this.endTime = endTime;
  }

  public long getEndIndex() {
    return endIndex;
  }

  public void setEndIndex(long endIndex) {
    this.endIndex = endIndex;
  }

  public long getUpdatesSinceTripChange() {
    return updatesSinceTripChange;
  }

  public void setUpdatesSinceTripChange(long updatesSinceTripChange) {
    this.updatesSinceTripChange = updatesSinceTripChange;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append("line=").append(lineRef);
    b.append(" vehicle=").append(vehicleRef);
    b.append(" location=").append(location);
    b.append(" start=").append(_format.format(startTime));
    b.append(" duration=").append(
        (endTime.getTime() - startTime.getTime()) / (1000 * 60)).append(" mins");
    b.append(" updates=").append(updatesSinceTripChange);
    return b.toString();
  }
}
