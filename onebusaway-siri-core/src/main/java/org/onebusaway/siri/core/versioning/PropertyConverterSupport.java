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
