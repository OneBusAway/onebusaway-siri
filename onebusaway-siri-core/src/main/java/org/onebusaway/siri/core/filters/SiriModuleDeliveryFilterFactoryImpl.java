package org.onebusaway.siri.core.filters;

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

      SiriModuleDeliveryFilter filter = (SiriModuleDeliveryFilter) createObjectForClassName(filterClassName);
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
      if (value.equals("null"))
        value = null;
      ElementPathModuleDeliveryFilter filter = new ElementPathModuleDeliveryFilter(
          key, value);
      collection.addFilter(filter);
    }

    return collection;
  }

  private Object createObjectForClassName(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      return clazz.newInstance();
    } catch (Throwable ex) {
      throw new SiriException("error instantiating class " + className, ex);
    }
  }

}
