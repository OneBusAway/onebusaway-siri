/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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
package org.onebusaway.siri.core.versioning;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.onebusaway.collections.MappingLibrary;

public class IntrospectionVersionConverter implements VersionConverter {

  private static VersionConverter _passThroughConverter = new PassThroughConverter();

  private ConcurrentMap<Class<?>, VersionConverter> _convertersBySourceType = new ConcurrentHashMap<Class<?>, VersionConverter>();

  private final TypeMappingStrategy _typeMappingStrategy;

  private final Set<TypeAndPropertyName> _propertiesToIgnore = new HashSet<TypeAndPropertyName>();

  private final Map<TypeAndPropertyName, PropertyConverterFactory> _propertyConverterFactories = new HashMap<IntrospectionVersionConverter.TypeAndPropertyName, PropertyConverterFactory>();

  public IntrospectionVersionConverter(TypeMappingStrategy typeMappingStrategy) {
    _typeMappingStrategy = typeMappingStrategy;
  }

  public void addPropertyToIgnore(Class<?> fromType, String propertyName) {
    _propertiesToIgnore.add(new TypeAndPropertyName(fromType, propertyName));
  }

  public void addPropertyConverterFactory(Class<?> fromType,
      String propertyName, PropertyConverterFactory factory) {
    _propertyConverterFactories.put(new TypeAndPropertyName(fromType,
        propertyName), factory);
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
    return sourceType.isPrimitive() || sourceType.isEnum()
        || name.startsWith("java") || name.startsWith("com.sun");
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

      if ("class".equals(name) || "declaringClass".equals(name))
        continue;

      TypeAndPropertyName fromPropertyKey = new TypeAndPropertyName(sourceType,
          name);

      if (_propertiesToIgnore.contains(fromPropertyKey))
        continue;

      PropertyDescriptor fromDesc = fromDescs.get(name);
      PropertyDescriptor toDesc = toDescs.get(name);

      PropertyConverterFactory factory = _propertyConverterFactories.get(fromPropertyKey);

      if (factory != null) {

        PropertyConverter converter = factory.createConverter(this, sourceType,
            targetType, name, fromDesc, toDesc);

        if (converter != null)
          converters.add(converter);

      } else {

        PropertyConverter converter = getPropertyConverter(sourceType,
            targetType, name, fromDesc, toDesc);

        if (converter != null)
          converters.add(converter);
      }
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

      Class<?> fromPropertyType = fromDesc.getPropertyType();
      Class<?> toPropertyType = toDesc.getPropertyType();

      if (readMethod == null && fromPropertyType == Boolean.class) {
        String getter = "is" + name.substring(0, 1).toUpperCase()
            + name.substring(1);
        readMethod = sourceType.getMethod(getter);
      }

      if (writeMethod == null && toPropertyType == List.class) {
        if (fromPropertyType == List.class) {
          return new ListPropertyConverter(this, readMethod,
              toDesc.getReadMethod());
        } else {
          return new ElementToListPropertyConverter(this, readMethod,
              toDesc.getReadMethod());
        }
      }

      if (writeMethod == null && toPropertyType == Boolean.TYPE) {
        String setter = "set" + name.substring(0, 1).toUpperCase()
            + name.substring(1);
        writeMethod = sourceType.getMethod(setter, Boolean.class);
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

  private static class TypeAndPropertyName {

    private final Class<?> type;

    private final String propertyName;

    public TypeAndPropertyName(Class<?> type, String propertyName) {
      if (type == null)
        throw new IllegalArgumentException();
      if (propertyName == null)
        throw new IllegalArgumentException();
      this.type = type;
      this.propertyName = propertyName;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + propertyName.hashCode();
      result = prime * result + type.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      TypeAndPropertyName other = (TypeAndPropertyName) obj;
      if (!propertyName.equals(other.propertyName))
        return false;
      if (!type.equals(other.type))
        return false;
      return true;
    }

  }

}
