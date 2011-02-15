package org.onebusaway.siri.core.versioning;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MethodPropertyConverter implements
    PropertyConverter {

  private final VersionConverter _converter;

  private final Method _from;

  private final Method _to;

  public MethodPropertyConverter(VersionConverter converter,
      Method from, Method to) {
    _converter = converter;
    _from = from;
    _to = to;
  }

  @Override
  public void convert(Object source, Object target) {
    Object sourceProperty = getSourcePropertyValue(source);

    if (sourceProperty == null)
      return;

    if (sourceProperty instanceof List<?>) {
      List<?> sourceList = (List<?>) sourceProperty;
      List<Object> targetList = new ArrayList<Object>(sourceList.size());
      for (Object sourceValue : sourceList) {
        Object targetProperty = _converter.convert(sourceValue);
        targetList.add(targetProperty);
        setTargetPropertyValue(target, targetList);
      }
    } else {
      Object targetProperty = _converter.convert(sourceProperty);
      setTargetPropertyValue(target, targetProperty);
    }
  }

  private Object getSourcePropertyValue(Object source) {

    try {
      return _from.invoke(source);
    } catch (Throwable ex) {
      throw new IllegalStateException("error getting property "
          + _from.getName() + " for " + source, ex);
    }
  }

  private void setTargetPropertyValue(Object target, Object targetPropertyValue) {
    try {
      _to.invoke(target, targetPropertyValue);
    } catch (Throwable ex) {
      throw new IllegalStateException("error setting property " + _to.getName()
          + " for " + target, ex);
    }
  }

}
