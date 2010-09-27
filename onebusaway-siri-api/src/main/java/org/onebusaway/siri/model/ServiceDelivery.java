package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.Calendar;
import java.util.List;

public class ServiceDelivery {
  
  @XStreamConverter(ISO8601GregorianCalendarConverterNoMillis.class)
  public Calendar ResponseTimestamp;
  
  public String ProducerRef;
  
  public boolean Status = true;
  
  public boolean MoreData = false;

  public VehicleMonitoringDelivery VehicleMonitoringDelivery;

  @XStreamImplicit
  public List<StopMonitoringDelivery> stopMonitoringDeliveries;
}
