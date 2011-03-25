package org.onebusaway.siri.core.versioning;

import java.util.HashMap;
import java.util.Map;

import org.onebusaway.collections.tuple.Pair;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.siri.core.exceptions.SiriUnknownVersionException;

public class SiriVersioning {

  private static final String SIRI_1_3_PACKAGE = "uk.org.siri.siri";

  private static final String SIRI_1_0_PACKAGE = "uk.org.siri";

  private static SiriVersioning _instance = new SiriVersioning();

  private Map<Pair<ESiriVersion>, VersionConverter> _convertersByVersions = new HashMap<Pair<ESiriVersion>, VersionConverter>();

  public static SiriVersioning getInstance() {
    return _instance;
  }

  private SiriVersioning() {

    /****
     * 1.0 to 1.3
     ****/

    TypeMappingStrategy mappingV10ToV13 = new PackageBasedTypeMappingStrategy(
        SIRI_1_0_PACKAGE, SIRI_1_3_PACKAGE);
    VersionConverter converterV10ToV13 = new IntrospectionVersionConverter(
        mappingV10ToV13);
    Pair<ESiriVersion> pairV10ToV13 = Tuples.pair(ESiriVersion.V1_0,
        ESiriVersion.V1_3);
    _convertersByVersions.put(pairV10ToV13, converterV10ToV13);

    /****
     * 1.3 to 1.0
     ****/

    TypeMappingStrategy mappingV13ToV10 = new PackageBasedTypeMappingStrategy(
        SIRI_1_3_PACKAGE, SIRI_1_0_PACKAGE);
    VersionConverter converterV13ToV10 = new IntrospectionVersionConverter(
        mappingV13ToV10);
    Pair<ESiriVersion> pairV13ToV10 = Tuples.pair(ESiriVersion.V1_3,
        ESiriVersion.V1_0);
    _convertersByVersions.put(pairV13ToV10, converterV13ToV10);
  }

  public ESiriVersion getDefaultVersion() {
    return ESiriVersion.V1_3;
  }
  
  public ESiriVersion getVersionOfObject(Object payload) {

    Class<?> type = payload.getClass();
    String typeName = type.getName();

    if (typeName.startsWith(SIRI_1_3_PACKAGE))
      return ESiriVersion.V1_3;
    else if (typeName.startsWith(SIRI_1_0_PACKAGE))
      return ESiriVersion.V1_0;

    throw new SiriUnknownVersionException(typeName);
  }

  public Object getPayloadAsVersion(Object payload, ESiriVersion targetVersion) {

    if (payload == null || targetVersion == null)
      return payload;

    ESiriVersion sourceVersion = getVersionOfObject(payload);

    if (sourceVersion != targetVersion) {

      VersionConverter converter = getVersionConverter(sourceVersion,
          targetVersion);

      payload = converter.convert(payload);
    }

    return payload;
  }

  /****
   * Private Methods
   ****/

  private VersionConverter getVersionConverter(ESiriVersion versionFrom,
      ESiriVersion versionTo) {

    Pair<ESiriVersion> pair = Tuples.pair(versionFrom, versionTo);

    return _convertersByVersions.get(pair);
  }
}
