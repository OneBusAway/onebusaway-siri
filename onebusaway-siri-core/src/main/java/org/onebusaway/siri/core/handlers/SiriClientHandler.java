package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.SiriClientRequest;

import uk.org.siri.siri.Siri;

public interface SiriClientHandler {

  public Siri handleRequestWithResponse(SiriClientRequest request);

  public void handleRequest(SiriClientRequest request);
}
