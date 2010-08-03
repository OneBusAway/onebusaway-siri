package org.onebusaway.siri.model;

import java.io.Serializable;

public class VehicleLocation implements Serializable {
  private static final long serialVersionUID = 1L;
  public double Longitude;
  public double Latitude;

  /* non-SIRI fields used by mta */
  public int Precision;
  
  public double getLatitude() {
    return Latitude;
  }
  
  public double getLongitude() {
    return Longitude;
  }
  
  public double getPrecision() {
    return Precision;
  }
  
  public void setLatitude(double latitude) {
    this.Latitude = latitude;
  }
}
