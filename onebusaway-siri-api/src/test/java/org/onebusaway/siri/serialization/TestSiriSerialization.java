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

package org.onebusaway.siri.serialization;

import org.onebusaway.siri.model.VehicleMonitoringDetailLevel;
import org.onebusaway.siri.model.VehicleMonitoringRequest;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

public class TestSiriSerialization extends XMLTestCase {
  @Test
  public void testSerialization() throws SAXException, IOException {
    VehicleMonitoringRequest request = new VehicleMonitoringRequest();
    Calendar calendar = Calendar.getInstance();
    calendar.set(2010, 6, 3, 1, 59, 26);
    int offset = TimeZone.getDefault().getOffset(calendar.getTimeInMillis()) / 1000;
    int offsethours = offset / 3600;
    int offsetminutes = (offset / 60) % 60;
    String tzoffset = String.format("%+03d:%02d", offsethours, offsetminutes);
    request.RequestTimestamp = calendar;
    request.VehicleMonitoringDetailLevel = VehicleMonitoringDetailLevel.normal;
    request.DirectionRef = "N";

    SiriSerializer serializer = new SiriSerializer();
    String output = serializer.serialize(request);
    String expected = "<VehicleMonitoringRequest version=\"1.0\">"
        + "<RequestTimestamp>2010-07-03T01:59:26" + tzoffset + "</RequestTimestamp>"
        + "<DirectionRef>N</DirectionRef>"
        + "<VehicleMonitoringDetailLevel>normal</VehicleMonitoringDetailLevel>"
        + "</VehicleMonitoringRequest>";

    XMLUnit.setIgnoreWhitespace(true);
    assertXMLEqual(expected, output);

    VehicleMonitoringRequest deserialized = (VehicleMonitoringRequest) serializer.deserialize(expected);
    assertEquals(calendar.getTimeZone(), deserialized.RequestTimestamp.getTimeZone()); 
    String reserialized = serializer.serialize(deserialized);
    
    assertXMLEqual("deserialization/reserialization test failed", expected, reserialized);
  }
}
