package org.onebusaway.siri.core.versioning;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ElementToListPropertyConverter implements PropertyConverter {

  private final VersionConverter _converter;

  private final Method _from;

  private final Method _to;

  public ElementToListPropertyConverter(VersionConverter converter,
      Method from, Method to) {
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

    Object targetProperty = _converter.convert(sourceProperty);
    List<Object> targetList = Arrays.asList(targetProperty);
    PropertyConverterSupport.setTargetPropertyValues(target, _to, targetList);
  }
}
