/**
 * Copyright (C) 2012 Google, Inc.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Test;

import uk.org.siri.siri.HalfOpenTimestampRangeStructure;
import uk.org.siri.siri.PtSituationElementStructure;
import uk.org.siri.siri.RoadSituationElementStructure.ValidityPeriod;

public class SiriLibraryTest {

  private static DateFormat _format = new SimpleDateFormat("HH:mm");

  @Test
  public void testSituationExpiration() {

    PtSituationElementStructure situation = new PtSituationElementStructure();
    assertFalse(SiriLibrary.isSituationExpired(situation, time("09:00")));
    assertTrue(SiriLibrary.isSituationPublishedOrValid(situation, time("09:00")));

    HalfOpenTimestampRangeStructure publicationWindow = new HalfOpenTimestampRangeStructure();
    publicationWindow.setStartTime(time("10:00"));
    situation.setPublicationWindow(publicationWindow);

    assertFalse(SiriLibrary.isSituationExpired(situation, time("09:00")));
    assertFalse(SiriLibrary.isSituationExpired(situation, time("11:00")));

    assertFalse(SiriLibrary.isSituationPublishedOrValid(situation,
        time("09:00")));
    assertTrue(SiriLibrary.isSituationPublishedOrValid(situation, time("11:00")));

    publicationWindow.setEndTime(time("10:30"));

    assertFalse(SiriLibrary.isSituationExpired(situation, time("09:00")));
    assertFalse(SiriLibrary.isSituationExpired(situation, time("10:15")));
    assertTrue(SiriLibrary.isSituationExpired(situation, time("11:00")));

    assertFalse(SiriLibrary.isSituationPublishedOrValid(situation,
        time("09:00")));
    assertTrue(SiriLibrary.isSituationPublishedOrValid(situation, time("10:15")));
    assertFalse(SiriLibrary.isSituationPublishedOrValid(situation,
        time("11:00")));

    ValidityPeriod period = new ValidityPeriod();
    period.setStartTime(time("10:20"));
    period.setEndTime(time("10:35"));
    situation.getValidityPeriod().add(period);

    assertFalse(SiriLibrary.isSituationExpired(situation, time("09:00")));
    assertFalse(SiriLibrary.isSituationExpired(situation, time("10:34")));
    assertTrue(SiriLibrary.isSituationExpired(situation, time("10:36")));

    assertFalse(SiriLibrary.isSituationPublishedOrValid(situation,
        time("09:00")));
    assertTrue(SiriLibrary.isSituationPublishedOrValid(situation, time("10:33")));
    assertFalse(SiriLibrary.isSituationPublishedOrValid(situation,
        time("11:00")));
  }

  @Test
  public void testIsTimeRangeActive() {
    HalfOpenTimestampRangeStructure range = new HalfOpenTimestampRangeStructure();
    assertFalse(SiriLibrary.isTimeRangeActive(range, time("08:00")));

    range.setStartTime(time("09:00"));
    assertFalse(SiriLibrary.isTimeRangeActive(range, time("08:00")));
    assertTrue(SiriLibrary.isTimeRangeActive(range, time("09:00")));
    assertTrue(SiriLibrary.isTimeRangeActive(range, time("10:00")));

    range.setStartTime(null);
    range.setEndTime(time("09:00"));
    assertTrue(SiriLibrary.isTimeRangeActive(range, time("08:00")));
    assertTrue(SiriLibrary.isTimeRangeActive(range, time("09:00")));
    assertFalse(SiriLibrary.isTimeRangeActive(range, time("10:00")));

    range.setStartTime(time("09:00"));
    range.setEndTime(time("10:00"));
    assertFalse(SiriLibrary.isTimeRangeActive(range, time("08:00")));
    assertTrue(SiriLibrary.isTimeRangeActive(range, time("09:00")));
    assertTrue(SiriLibrary.isTimeRangeActive(range, time("09:30")));
    assertTrue(SiriLibrary.isTimeRangeActive(range, time("10:00")));
    assertFalse(SiriLibrary.isTimeRangeActive(range, time("11:00")));
  }

  @Test
  public void testIsTimeRangeActiveOrUpcoming() {
    HalfOpenTimestampRangeStructure range = new HalfOpenTimestampRangeStructure();
    assertFalse(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("08:00")));

    range.setStartTime(time("09:00"));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("08:00")));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("09:00")));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("10:00")));

    range.setStartTime(null);
    range.setEndTime(time("09:00"));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("08:00")));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("09:00")));
    assertFalse(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("10:00")));

    range.setStartTime(time("09:00"));
    range.setEndTime(time("10:00"));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("08:00")));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("09:00")));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("09:30")));
    assertTrue(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("10:00")));
    assertFalse(SiriLibrary.isTimeRangeActiveOrUpcoming(range, time("11:00")));
  }

  private static Date time(String value) {
    try {
      return _format.parse(value);
    } catch (ParseException e) {
      throw new IllegalStateException(e);
    }
  }
}
