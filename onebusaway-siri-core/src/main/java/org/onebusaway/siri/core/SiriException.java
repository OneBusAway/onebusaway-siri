package org.onebusaway.siri.core;

public abstract class SiriException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SiriException(String message) {
    super(message);
  }

  public SiriException(String message, Throwable ex) {
    super(message, ex);
  }

  public SiriException(Throwable ex) {
    super(ex);
  }
}
