/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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
package org.onebusaway.siri.core.filters;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;

/**
 * An interface to filter the individual
 * {@link AbstractServiceDeliveryStructure} elements that are carried in a
 * {@link ServiceDelivery} container.
 * 
 * @author bdferris
 */
public interface SiriModuleDeliveryFilter {

  /**
   * <p>
   * A delivery filter allows you to modify the contents of a
   * {@link AbstractServiceDeliveryStructure}, as contained within a
   * {@link ServiceDelivery} payload, before it is sent to a client.
   * </p>
   * 
   * <p>
   * Your filter implementation can return the input delivery object, perhaps
   * modified in some way. It can also return an entirely new delivery object or
   * null to indicate no result should be published to the client.
   * </p>
   * 
   * @param delivery the parent ServiceDelivery object, for your reference
   * @param moduleDelivery the module delivery structure to filter
   * 
   * @return the updated delivery structure or null for empty result
   */
  public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
      AbstractServiceDeliveryStructure moduleDelivery);
}
