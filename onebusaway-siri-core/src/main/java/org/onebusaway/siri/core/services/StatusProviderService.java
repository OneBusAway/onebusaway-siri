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

import java.util.Map;

/**
 * A simple plugin interface to provide status information in the form of
 * key-value pairs. This is used for diagnostic / logging of the application
 * state.
 * 
 * @author bdferris
 * 
 */
public interface StatusProviderService {

  /**
   * Provide status information in the form of String key-value pairs by adding
   * them to the specified status map.
   * @param status
   */
  public void getStatus(Map<String, String> status);
}
