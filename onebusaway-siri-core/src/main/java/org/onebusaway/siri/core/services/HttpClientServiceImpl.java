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

import javax.inject.Singleton;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.onebusaway.siri.core.exceptions.SiriConnectionException;

/**
 * Simple implementation of {@link HttpClientService}.
 * 
 * @author bdferris
 * 
 */
@Singleton
class HttpClientServiceImpl implements HttpClientService {

  @Override
  public HttpResponse executeHttpMethod(HttpClient client,
      HttpUriRequest request) throws SiriConnectionException {
    try {
      return client.execute(request);
    } catch (Exception ex) {
      throw new SiriConnectionException("error connecting to url "
          + request.getURI(), ex);
    }
  }
}
