package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamConverter;

import java.util.Calendar;

public class MonitoredStopVisit {

  @XStreamConverter(ISO8601GregorianCalendarConverterNoMillis.class)
  Calendar RecordedAtTime;

  /* unused */
  String ItemIdentifier;
  String MonitoringRef;

  /* unused */
  String ClearDownRef;

  MonitoredVehicleJourney MonitoredVehicleJourney;

  /* unused */
  String StopVisitNode;
}
