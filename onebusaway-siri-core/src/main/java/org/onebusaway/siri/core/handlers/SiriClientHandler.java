package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.exceptions.SiriException;

import uk.org.siri.siri.Siri;

public interface SiriClientHandler {

  /**
   * @param request
   * @return the resulting SIRI data, or null if no response was received
   * @throws SiriException on error
   */
  public Siri handleRequestWithResponse(SiriClientRequest request) throws SiriException;

  public void handleRequest(SiriClientRequest request);
  
  public void handleRequestReconnectIfApplicable(SiriClientRequest request);
}
