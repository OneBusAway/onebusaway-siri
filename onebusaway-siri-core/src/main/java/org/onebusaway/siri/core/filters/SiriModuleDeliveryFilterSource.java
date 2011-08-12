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

public class SiriModuleDeliveryFilterSource {

  private SiriModuleDeliveryFilterMatcher matcher;

  private SiriModuleDeliveryFilter filter;

  public SiriModuleDeliveryFilterMatcher getMatcher() {
    return matcher;
  }

  public void setMatcher(SiriModuleDeliveryFilterMatcher matcher) {
    this.matcher = matcher;
  }

  public SiriModuleDeliveryFilter getFilter() {
    return filter;
  }

  public void setFilter(SiriModuleDeliveryFilter filter) {
    this.filter = filter;
  }
}
