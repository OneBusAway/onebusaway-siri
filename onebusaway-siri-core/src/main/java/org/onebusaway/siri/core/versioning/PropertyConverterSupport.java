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
package org.onebusaway.siri.core.versioning;

import java.lang.reflect.Method;
import java.util.Collection;

import org.onebusaway.siri.core.exceptions.SiriException;

public class PropertyConverterSupport {

  public static Object getSourcePropertyValue(Object source,
      Method propertyReadMethod) {
    try {
      return propertyReadMethod.invoke(source);
    } catch (Throwable ex) {
      throw new SiriException("error getting property "
          + propertyReadMethod.getName() + " for " + source, ex);
    }
  }

  public static void setTargetPropertyValue(Object target,
      Method propertyWriteMethod, Object targetPropertyValue) {
    try {
      propertyWriteMethod.invoke(target, targetPropertyValue);
    } catch (Throwable ex) {
      throw new SiriException("error setting property "
          + propertyWriteMethod.getName() + " for " + target, ex);
    }
  }

  public static void setTargetPropertyValues(Object target,
      Method propertyReadMethod, Collection<?> targetListValues) {
    try {
      @SuppressWarnings("unchecked")
      Collection<Object> targetList = (Collection<Object>) propertyReadMethod.invoke(target);
      targetList.clear();
      targetList.addAll(targetListValues);
    } catch (Throwable ex) {
      throw new SiriException("error getting property "
          + propertyReadMethod.getName() + " for " + target, ex);
    }
  }
}
