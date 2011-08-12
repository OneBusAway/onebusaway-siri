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

import java.util.HashMap;
import java.util.Map;

public enum ESiriVersion {

  /**
   * 
   */
  V1_0("1.0"),

  /**
   * 
   */
  V1_3("1.3");

  private final String _versionId;

  private ESiriVersion(String versionId) {
    _versionId = versionId;
  }

  public String getVersionId() {
    return _versionId;
  }

  private static Map<String, ESiriVersion> _versionsByVersionId = new HashMap<String, ESiriVersion>();

  static {
    for (ESiriVersion version : ESiriVersion.values())
      _versionsByVersionId.put(version.getVersionId(), version);
  }

  public static ESiriVersion getVersionForVersionId(String versionId) {
    return _versionsByVersionId.get(versionId);
  }
}
