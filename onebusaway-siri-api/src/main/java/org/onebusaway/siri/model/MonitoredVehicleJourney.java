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

import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.List;

import javax.xml.datatype.Duration;

public class MonitoredVehicleJourney {
  public String LineRef;
  public String DirectionRef;
  public FramedVehicleJourneyRef FramedVehicleJourneyRef;

  public String PublishedLineName;
  public String OperatorRef;
  public String ProductCategoryRef;
  public String ServiceFeatureRef;
  public String OriginName;
  
  @XStreamImplicit
  public List<Via> Via;
  
  public String DestinationRef;

  /* unused */
  public String DestinationName;
  
  public String JourneyNote;
  
  public boolean Monitored = true;
  /* unused */
  public boolean InCongestion = false;
  
  public VehicleLocation VehicleLocation;
  
  public Double Bearing;
  public String ProgressRate; //fixme: enum?
  
  public Duration Delay;
  
  public String ProgressStatus; //fixme: enum?
  
  public String BlockRef;
  
  public String CourseOfJourneyRef;
  public String VehicleRef;
  
  public List <PreviousCall> PreviousCalls;
  
  public List <OnwardCall> OnwardCalls;

  public String OriginRef;

  public DistanceExtensions Extensions;
}
