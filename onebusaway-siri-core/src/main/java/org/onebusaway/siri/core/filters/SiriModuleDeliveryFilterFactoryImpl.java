/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2014 Google, Inc.
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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

import org.onebusaway.siri.core.exceptions.SiriException;

public class SiriModuleDeliveryFilterFactoryImpl {

  private static final String ARG_FILTER_PREFIX = "Filter.";

  private static final String ARG_FILTER_TYPE = "Filter.Type";

  private static final String ARG_FILTER_CLASS = "Filter.Class";

  private static final String ARG_FILTER_TYPE_ELEMENTS = "Elements";

  private static final String ARG_FILTER_ELEMENT_PREFIX = ARG_FILTER_PREFIX
      + "Element.";

  public SiriModuleDeliveryFilter create(Map<String, String> filterArgs) {

    String filterType = filterArgs.remove(ARG_FILTER_TYPE);

    if (filterType != null) {
      if (filterType.equals(ARG_FILTER_TYPE_ELEMENTS)) {
        return createPropertyFilter(filterArgs);
      }
      throw new SiriException("uknown filter type: " + filterType);
    }

    String filterClassName = filterArgs.remove(ARG_FILTER_CLASS);

    if (filterClassName != null) {
      Class<? extends SiriModuleDeliveryFilter> filterClass = resolveFilterClass(filterClassName);
      SiriModuleDeliveryFilter filter = (SiriModuleDeliveryFilter) createObjectForClass(filterClass);
      setFilterArguments(filter, filterArgs);
      return filter;
    }

    throw new SiriException("expected argument \"" + ARG_FILTER_TYPE
        + "\" or \"" + ARG_FILTER_CLASS + "\"");
  }

  private SiriModuleDeliveryFilter createPropertyFilter(
      Map<String, String> filterArgs) {

    ModuleDeliveryFilterCollection collection = new ModuleDeliveryFilterCollection();

    for (Iterator<Map.Entry<String, String>> it = filterArgs.entrySet().iterator(); it.hasNext();) {

      Map.Entry<String, String> entry = it.next();

      String key = entry.getKey();
      if (!key.startsWith(ARG_FILTER_ELEMENT_PREFIX))
        continue;
      it.remove();
      key = key.substring(ARG_FILTER_ELEMENT_PREFIX.length());
      String value = entry.getValue();
      ElementPathModuleDeliveryFilter filter = new ElementPathModuleDeliveryFilter(
          key, value);
      collection.addFilter(filter);
    }

    return collection;
  }

  @SuppressWarnings("unchecked")
  private Class<? extends SiriModuleDeliveryFilter> resolveFilterClass(
      String filterClassName) {
    try {
      return (Class<? extends SiriModuleDeliveryFilter>) Class.forName(filterClassName);
    } catch (ClassNotFoundException ex) {
      String exapndedName = this.getClass().getPackage().getName() + "."
          + filterClassName;
      try {
        return (Class<? extends SiriModuleDeliveryFilter>) Class.forName(exapndedName);
      } catch (ClassNotFoundException ex2) {
        throw new SiriException("error resolving filter class "
            + filterClassName, ex);
      }
    } catch (Throwable ex) {
      throw new SiriException(
          "error resolving filter class " + filterClassName, ex);
    }
  }

  private Object createObjectForClass(
      Class<? extends SiriModuleDeliveryFilter> filterClass) {
    try {
      return filterClass.newInstance();
    } catch (Throwable ex) {
      throw new SiriException("error instantiating class " + filterClass, ex);
    }
  }

  private void setFilterArguments(SiriModuleDeliveryFilter filter,
      Map<String, String> filterArgs) {
    BeanInfo info = null;
    try {
      info = Introspector.getBeanInfo(filter.getClass());
    } catch (IntrospectionException ex) {
      throw new SiriException("Could not inspect class "
          + filter.getClass().getName());
    }
    PropertyDescriptor[] properties = info.getPropertyDescriptors();
    for (PropertyDescriptor property : properties) {
      Method method = property.getWriteMethod();
      String value = filterArgs.remove(property.getName());
      if (method != null && value != null) {
        try {
          method.invoke(filter, value);
        } catch (Exception ex) {
          throw new SiriException("Error setting filter property "
              + property.getName() + " on " + filter.getClass().getName(), ex);
        }
      }
    }
  }
}
