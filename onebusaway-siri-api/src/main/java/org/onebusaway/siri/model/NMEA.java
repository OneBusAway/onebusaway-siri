package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.List;

public class NMEA {
  @XStreamImplicit(itemFieldName = "Sentence")
  public List<String> sentences;
}
