package org.onebusaway.siri.core.versioning;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MethodPropertyConverter implements PropertyConverter {

  private final VersionConverter _converter;

  private final Method _from;

  private final Method _to;

  public MethodPropertyConverter(VersionConverter converter, Method from,
      Method to) {
    _converter = converter;
    _from = from;
    _to = to;
  }

  @Override
  public void convert(Object source, Object target) {

    Object sourceProperty = PropertyConverterSupport.getSourcePropertyValue(
        source, _from);

    if (sourceProperty == null)
      return;

    if (sourceProperty instanceof List<?>) {
      List<?> sourceList = (List<?>) sourceProperty;
      List<Object> targetList = new ArrayList<Object>(sourceList.size());
      for (Object sourceValue : sourceList) {
        Object targetProperty = _converter.convert(sourceValue);
        targetList.add(targetProperty);
        PropertyConverterSupport.setTargetPropertyValue(target, _to, targetList);
      }
    } else {
      Object targetProperty = _converter.convert(sourceProperty);
      PropertyConverterSupport.setTargetPropertyValue(target, _to,
          targetProperty);
    }
  }
}
