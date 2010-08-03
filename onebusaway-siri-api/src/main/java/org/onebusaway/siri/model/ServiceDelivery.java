package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamConverter;

import java.util.Calendar;

public class ServiceDelivery {
  
  @XStreamConverter(ISO8601GregorianCalendarConverterNoMillis.class)
  public Calendar ResponseTimestamp;
  
  public String ProducerRef;
  
  public boolean Status = true;
  
  public boolean MoreData = false;

  public VehicleMonitoringDelivery VehicleMonitoringDelivery;

}
