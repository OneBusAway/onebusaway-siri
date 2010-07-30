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

import com.thoughtworks.xstream.converters.extended.ISO8601GregorianCalendarConverter;

import java.util.Calendar;

public class ISO8601GregorianCalendarConverterNoMillis extends ISO8601GregorianCalendarConverter {
  @Override
  public String toString(Object source) {
    Calendar calendar = (Calendar) source;
    String withMillis = super.toString(calendar);
    //remove milliseconds though string manipulation
    int startMillis = withMillis.indexOf(".");
    return withMillis.substring(0, startMillis) + 
    withMillis.substring(startMillis + 4);
  }
}
