package org.onebusaway.siri.core.versioning;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.onebusaway.collections.MappingLibrary;

public class IntrospectionVersionConverter implements VersionConverter {

  private static VersionConverter _passThroughConverter = new PassThroughConverter();

  private ConcurrentMap<Class<?>, VersionConverter> _convertersBySourceType = new ConcurrentHashMap<Class<?>, VersionConverter>();

  private final TypeMappingStrategy _typeMappingStrategy;

  public IntrospectionVersionConverter(TypeMappingStrategy typeMappingStrategy) {
    _typeMappingStrategy = typeMappingStrategy;
  }

  @Override
  public Object convert(Object source) {

    Class<? extends Object> sourceType = source.getClass();

    VersionConverter converter = getConverterForSourceType(sourceType);
    if (converter == null)
      throw new IllegalStateException(
          "version converter not found for source type " + sourceType.getName());
    return converter.convert(source);
  }

  private VersionConverter getConverterForSourceType(Class<?> sourceType) {

    VersionConverter converter = _convertersBySourceType.get(sourceType);

    if (converter == null) {
      VersionConverter newConverter = createConverter(sourceType);
      converter = _convertersBySourceType.putIfAbsent(sourceType, newConverter);
      if (converter == null)
        converter = newConverter;
    }

    return converter;
  }

  private VersionConverter createConverter(Class<?> sourceType) {

    if (isPrimitiveType(sourceType))
      return _passThroughConverter;

    Class<?> targetType = determineTargetTypeForSourceType(sourceType);

    List<PropertyConverter> converters = getPropertyConvertersForTypes(
        sourceType, targetType);

    return new VersionConverterImpl(targetType, converters);
  }

  private boolean isPrimitiveType(Class<? extends Object> sourceType) {
    String name = sourceType.getName();
    return name.startsWith("java") || name.startsWith("com.sun");
  }

  private Class<?> determineTargetTypeForSourceType(Class<?> sourceType) {
    Class<?> targetType = _typeMappingStrategy.getTargetTypeForSourceType(sourceType);
    if (targetType == null)
      throw new IllegalStateException("could not find type mapping for "
          + sourceType.getName());
    return targetType;
  }

  private List<PropertyConverter> getPropertyConvertersForTypes(
      Class<?> sourceType, Class<?> targetType) {

    BeanInfo fromInfo = null;
    BeanInfo toInfo = null;

    try {

      fromInfo = Introspector.getBeanInfo(sourceType);
      toInfo = Introspector.getBeanInfo(targetType);

    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }

    Map<String, PropertyDescriptor> fromDescs = getDescriptorsByName(fromInfo);
    Map<String, PropertyDescriptor> toDescs = getDescriptorsByName(toInfo);

    List<PropertyConverter> converters = new ArrayList<PropertyConverter>();

    for (String name : fromDescs.keySet()) {

      if (!toDescs.containsKey(name))
        continue;

      if ("class".equals(name))
        continue;

      PropertyDescriptor fromDesc = fromDescs.get(name);
      PropertyDescriptor toDesc = toDescs.get(name);

      PropertyConverter converter = getPropertyConverter(sourceType,
          targetType, name, fromDesc, toDesc);

      if (converter != null)
        converters.add(converter);
    }

    return converters;
  }

  private Map<String, PropertyDescriptor> getDescriptorsByName(BeanInfo fromInfo) {
    PropertyDescriptor[] descs = fromInfo.getPropertyDescriptors();
    return MappingLibrary.mapToValue(Arrays.asList(descs), "name");
  }

  private PropertyConverter getPropertyConverter(Class<?> sourceType,
      Class<?> targetType, String name, PropertyDescriptor fromDesc,
      PropertyDescriptor toDesc) {

    try {
      Method readMethod = fromDesc.getReadMethod();
      Method writeMethod = toDesc.getWriteMethod();

      if (readMethod == null && fromDesc.getPropertyType() == Boolean.class) {
        String getter = "is" + name.substring(0, 1).toUpperCase()
            + name.substring(1);
        readMethod = sourceType.getMethod(getter);
      }

      if (writeMethod == null && fromDesc.getPropertyType() == List.class) {
        return new ListPropertyConverter(this, readMethod,
            toDesc.getReadMethod());
      }

      if (readMethod == null)
        throw new IllegalStateException("no read method for property \"" + name
            + "\" of source type " + sourceType.getName());

      if (writeMethod == null)
        throw new IllegalStateException("no write method for property \""
            + name + "\" of target type " + targetType.getName());

      return new MethodPropertyConverter(this, readMethod, writeMethod);
    } catch (Throwable ex) {
      throw new IllegalStateException(ex);
    }

  }

  private static class PassThroughConverter implements VersionConverter {
    @Override
    public Object convert(Object source) {
      return source;
    }
  }

}
