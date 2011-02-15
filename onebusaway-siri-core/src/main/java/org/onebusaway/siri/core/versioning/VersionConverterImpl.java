package org.onebusaway.siri.core.versioning;

import java.util.List;

class VersionConverterImpl implements VersionConverter {

  private final Class<?> _targetType;

  private final List<PropertyConverter> _converters;

  public VersionConverterImpl(Class<?> targetType,
      List<PropertyConverter> converters) {
    _targetType = targetType;
    _converters = converters;
  }

  @Override
  public Object convert(Object source) {

    Object target = newInstance();

    for (PropertyConverter converter : _converters)
      converter.convert(source, target);

    return target;
  }

  private Object newInstance() {
    try {
      return _targetType.newInstance();
    } catch (Throwable ex) {
      throw new IllegalStateException("could not instantiate target type "
          + _targetType.getName(), ex);
    }
  }
}
