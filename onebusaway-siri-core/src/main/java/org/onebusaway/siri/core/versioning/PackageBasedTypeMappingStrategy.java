package org.onebusaway.siri.core.versioning;

public class PackageBasedTypeMappingStrategy implements TypeMappingStrategy {

  private final String _sourcePackage;

  private final String _targetPackage;

  public PackageBasedTypeMappingStrategy(String sourcePackage,
      String targetPackage) {
    _sourcePackage = sourcePackage;
    _targetPackage = targetPackage;
  }

  @Override
  public Class<?> getTargetTypeForSourceType(Class<?> sourceType) {

    String name = sourceType.getName();
    name = name.replace(_sourcePackage, _targetPackage);

    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

}
