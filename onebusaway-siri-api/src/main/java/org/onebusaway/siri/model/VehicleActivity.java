/*
 * Copyright 2010, OpenPlans
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.     
 */

package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

import java.util.Calendar;

@XStreamAlias("VehicleActivity")
public class VehicleActivity {

  @XStreamConverter(ISO8601GregorianCalendarConverterNoMillis.class)
  public Calendar RecordedAtTime;
  
  /**
   * This is required but not explained by the SIRI spec.
   * So we'll give it a random value.
   */
  public String ItemIdentifier = "ITEMID";
  
  @XStreamConverter(ISO8601GregorianCalendarConverterNoMillis.class)
  public Calendar ValidUntilTime;
  
  /** 
   * This refers to a vehicle monitoring area (whatever that is).
   * We'll assume that all vehicles share the same area
   */
  public String VehicleMonitoringRef = "AREA";
  
  public ProgressBetweenStops ProgressBetweenStops;
  
  public MonitoredVehicleJourney MonitoredVehicleJourney;
  
  public String VehicleActivityNote;

  /* mta extensions */
  public Extensions Extensions;
  
}
