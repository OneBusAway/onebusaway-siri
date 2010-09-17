package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("Extensions")
public class DistanceExtensions {
  public Double DistanceAlongRoute;
  public Double DistanceFromCall;
  public int StopsFromCall;
}
