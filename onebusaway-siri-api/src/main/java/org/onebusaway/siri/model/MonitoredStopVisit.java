package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

import java.util.Calendar;

@XStreamAlias("MonitoredStopVisit")
public class MonitoredStopVisit {

  @XStreamConverter(ISO8601GregorianCalendarConverterNoMillis.class)
  public Calendar RecordedAtTime;

  /* unused */
  public String ItemIdentifier;
  public String MonitoringRef;

  /* unused */
  public String ClearDownRef;

  public MonitoredVehicleJourney MonitoredVehicleJourney;

  /* unused */
  public String StopVisitNode;
}
