package org.onebusaway.siri.core.versioning;

import java.lang.reflect.Method;
import java.util.List;

public class PropertyConverterSupport {

  public static Object getSourcePropertyValue(Object source, Method method) {
    try {
      return method.invoke(source);
    } catch (Throwable ex) {
      throw new IllegalStateException("error getting property "
          + method.getName() + " for " + source, ex);
    }
  }

  public static void setTargetPropertyValue(Object target, Method method,
      Object targetPropertyValue) {
    try {
      method.invoke(target, targetPropertyValue);
    } catch (Throwable ex) {
      throw new IllegalStateException("error setting property "
          + method.getName() + " for " + target, ex);
    }
  }

  public static void setTargetPropertyValues(Object target, Method method,
      List<?> targetListValues) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> targetList = (List<Object>) method.invoke(target);
      targetList.clear();
      targetList.addAll(targetListValues);
    } catch (Throwable ex) {
      throw new IllegalStateException("error getting property "
          + method.getName() + " for " + target, ex);
    }
  }
}
