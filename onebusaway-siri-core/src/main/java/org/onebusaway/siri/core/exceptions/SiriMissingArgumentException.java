package org.onebusaway.siri.core.exceptions;

public class SiriMissingArgumentException extends SiriException {

  private static final long serialVersionUID = 1L;

  private final String _name;

  public SiriMissingArgumentException(String name) {
    super("missing argument: " + name);
    _name = name;
  }

  public String getName() {
    return _name;
  }
}
