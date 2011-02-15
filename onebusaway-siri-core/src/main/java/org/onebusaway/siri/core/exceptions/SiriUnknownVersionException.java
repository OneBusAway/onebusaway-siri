package org.onebusaway.siri.core.exceptions;

public class SiriUnknownVersionException extends SiriException {

  private static final long serialVersionUID = 1L;

  private final String _version;

  public SiriUnknownVersionException(String version) {
    super("unknown siri version: " + version);
    _version = version;
  }

  public String getVersion() {
    return _version;
  }
}
