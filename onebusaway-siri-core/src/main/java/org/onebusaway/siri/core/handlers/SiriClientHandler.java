/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2011 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.core.handlers;

import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.exceptions.SiriException;

import uk.org.siri.siri.Siri;

/**
 * Interface that encapsulates basic siri client request mechanisms, including
 * making a request, with or without a response.
 * 
 * @author bdferris
 * @see SiriClient
 */
public interface SiriClientHandler {

  /**
   * Submit a {@link SiriClientRequest} to a remote SIRI endpoint and receive a
   * response in return. Note that the endpoint does not have to deliver a
   * response directly, but instead may deliver it asynchronously, or not at
   * all.
   * 
   * @param request the SIRI client request
   * @return the resulting SIRI data, or null if no response was received
   * @throws SiriException on error
   */
  public Siri handleRequestWithResponse(SiriClientRequest request)
      throws SiriException;

  /**
   * Submit a {@link SiriClientRequest} to a remote SIRI endpoint.
   * 
   * @param request the SIRI client request
   */
  public void handleRequest(SiriClientRequest request);

  /**
   * If a previous request failed, attempt it again, honoring the
   * {@link SiriClientRequest} reconnection settings to determine if the request
   * should be re-attempted.
   * 
   * @param request the SIRI client request
   */
  public void handleRequestReconnectIfApplicable(SiriClientRequest request);
}
