package org.onebusaway.siri.core;

import uk.org.siri.siri.ServiceRequest;

public class SiriClientServiceRequest extends AbstractSiriClientRequest {

  private ServiceRequest request;

  public ServiceRequest getRequest() {
    return request;
  }

  public void setRequest(ServiceRequest request) {
    this.request = request;
  }
}
