package org.onebusaway.siri.core.versioning;

import java.lang.reflect.Method;

public class FixedValuePropertyConverter implements PropertyConverter {

  private Method _writeMethod;
  private Object _value;

  public FixedValuePropertyConverter(Method writeMethod, Object value) {
    _writeMethod = writeMethod;
    _value = value;
  }

  @Override
  public void convert(Object source, Object target) {
    PropertyConverterSupport.setTargetPropertyValue(target, _writeMethod,
        _value);
  }
}
