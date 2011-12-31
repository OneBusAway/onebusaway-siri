/**
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
package org.onebusaway.siri.core.services;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;

/**
 * We encapsulate {@link HttpClient} operations, primarily to assist with unit
 * testing but also as an extension point for anyone who wants to tweak the
 * client behavior.
 * 
 * @author bdferris
 * 
 */
public interface HttpClientService {

  /**
   * Execute the specified HTTP request on the specified client.
   * 
   * @param client
   * @param request
   * @return the response
   * @throws SiriConnectionException
   */
  public HttpResponse executeHttpMethod(HttpClient client,
      HttpUriRequest request) throws SiriConnectionException;
}
