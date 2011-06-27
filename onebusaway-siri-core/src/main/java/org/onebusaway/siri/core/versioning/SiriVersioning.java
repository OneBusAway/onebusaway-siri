package org.onebusaway.siri.core.versioning;

import java.beans.PropertyDescriptor;
import java.util.HashMap;
import java.util.Map;

import org.onebusaway.collections.tuple.Pair;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.siri.core.exceptions.SiriUnknownVersionException;

public class SiriVersioning {

  public static final String SIRI_1_3_PACKAGE = "uk.org.siri.siri";

  public static final String SIRI_1_0_PACKAGE = "uk.org.siri";

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
    IntrospectionVersionConverter converterV10ToV13 = new IntrospectionVersionConverter(
        mappingV10ToV13);

    VersionPropertyConverterFactory v13VersionFactory = new VersionPropertyConverterFactory(
        "1.3");

    converterV10ToV13.addPropertyConverterFactory(uk.org.siri.Siri.class,
        "version", v13VersionFactory);
    converterV10ToV13.addPropertyConverterFactory(
        uk.org.siri.VehicleMonitoringDeliveryStructure.class, "version",
        v13VersionFactory);

    Pair<ESiriVersion> pairV10ToV13 = Tuples.pair(ESiriVersion.V1_0,
        ESiriVersion.V1_3);
    _convertersByVersions.put(pairV10ToV13, converterV10ToV13);

    /****
     * 1.3 to 1.0
     ****/

    TypeMappingStrategy mappingV13ToV10 = new PackageBasedTypeMappingStrategy(
        SIRI_1_3_PACKAGE, SIRI_1_0_PACKAGE);
    IntrospectionVersionConverter converterV13ToV10 = new IntrospectionVersionConverter(
        mappingV13ToV10);

    VersionPropertyConverterFactory v10VersionFactory = new VersionPropertyConverterFactory(
        "1.0");

    converterV13ToV10.addPropertyConverterFactory(uk.org.siri.siri.Siri.class,
        "version", v10VersionFactory);
    converterV13ToV10.addPropertyConverterFactory(
        uk.org.siri.siri.VehicleMonitoringDeliveryStructure.class, "version",
        v10VersionFactory);

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

  private static class VersionPropertyConverterFactory implements
      PropertyConverterFactory {

    private String _version;

    public VersionPropertyConverterFactory(String version) {
      _version = version;
    }

    @Override
    public PropertyConverter createConverter(VersionConverter versionConverter,
        Class<?> sourceType, Class<?> targetType, String fromName,
        PropertyDescriptor fromDesc, PropertyDescriptor toDesc) {

      return new FixedValuePropertyConverter(toDesc.getWriteMethod(), _version);
    }
  }
}
