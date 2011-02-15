package org.onebusaway.siri.core.versioning;

public interface TypeMappingStrategy {
  public Class<?> getTargetTypeForSourceType(Class<?> sourceType);
}
