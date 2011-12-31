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
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * A simple service to provide status information in the form of key-value
 * pairs, as retreived from a number of {@link StatusProviderService} instances.
 * This is used for diagnostic / logging of the application state.
 * 
 * @author bdferris
 * @see StatusProviderService
 */
@Singleton
public class StatusService {

  public static final String PROVIDERS_NAME = "org.onebusaway.siri.core.services.StatusService.providers";

  private Set<StatusProviderService> _providers;

  @Inject
  public void setProviders(
      @Named(PROVIDERS_NAME) Set<StatusProviderService> providers) {
    _providers = providers;
  }

  /**
   * Provide status information in the form of String key-value pairs by adding
   * them to the specified status map.
   * @param status
   */
  public void getStatus(Map<String, String> status) {
    for (StatusProviderService provider : _providers) {
      provider.getStatus(status);
    }
  }
}
