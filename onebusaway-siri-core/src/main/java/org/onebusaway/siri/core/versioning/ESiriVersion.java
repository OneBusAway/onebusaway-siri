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
