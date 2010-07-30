package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamConverter;

import java.util.Calendar;

public class ServiceDelivery {
  
  @XStreamConverter(ISO8601GregorianCalendarConverterNoMillis.class)
  Calendar ResponseTimestamp;
  
  String ProducerRef;
  
  boolean Status;
  
  boolean MoreData;
  
  VehicleMonitoringDelivery VehicleMonitoringDelivery;
}
