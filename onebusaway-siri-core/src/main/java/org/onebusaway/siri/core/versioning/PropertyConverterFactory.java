package org.onebusaway.siri.core.versioning;

import java.beans.PropertyDescriptor;

public interface PropertyConverterFactory {
  public PropertyConverter createConverter(VersionConverter versionConverter,
      Class<?> sourceType, Class<?> targetType, String fromName,
      PropertyDescriptor fromDesc, PropertyDescriptor toDesc);
}
