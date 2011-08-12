/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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
package org.onebusaway.siri.core;

/**
 * Enum capturing the various functional modules of the SIRI spec.
 * 
 * @author bdferris
 */
public enum ESiriModuleType {

  /**
   * 
   */
  PRODUCTION_TIMETABLE,

  /**
   * 
   */
  ESTIMATED_TIMETABLE,

  /**
   * 
   */
  STOP_TIMETABLE,

  /**
   * 
   */
  STOP_MONITORING,

  /**
   * 
   */
  VEHICLE_MONITORING,

  /**
   * 
   */
  CONNECTION_TIMETABLE,

  /**
   * 
   */
  CONNECTION_MONITORING_FEEDER,

  /**
   * 
   */
  CONNECTION_MONITORING_DISTRIBUTOR,

  /**
   * 
   */
  GENERAL_MESSAGE,

  /**
   * 
   */
  FACILITY_MONITORING,

  /**
   * 
   */
  SITUATION_EXCHANGE
}
