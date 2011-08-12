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
