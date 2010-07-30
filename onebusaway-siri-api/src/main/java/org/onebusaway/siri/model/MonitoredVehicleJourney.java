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
  String LineRef;
  String DirectionRef;
  FramedVehicleJourneyRef FramedVehicleJourneyRef;

  String PublishedLineName;
  String OperatorRef;
  String ProductCategoryRef;
  String ServiceFeatureRef;
  String OriginName;
  
  @XStreamImplicit
  List<Via> Via;
  
  String DestinationRef;
  String DestinationName;
  String JourneyNote;
  
  boolean Monitored;
  boolean InCongestion;
  
  VehicleLocation VehicleLocation;
  
  double Bearing;
  String ProgressRate; //fixme: enum?
  
  Duration Delay;
  
  String ProgressStatus; //fixme: enum?
  
  String BlockRef;
  
  String CourseOfJourneyRef;
  String VehicleRef;
  
  List <PreviousCall> PreviousCalls;
  
  List <OnwardCall> OnwardCalls;
}
