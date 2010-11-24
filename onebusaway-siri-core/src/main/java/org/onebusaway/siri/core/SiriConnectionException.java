package org.onebusaway.siri.core;

public class SiriConnectionException extends SiriException {

  private static final long serialVersionUID = 1L;

  public SiriConnectionException(String message) {
    super(message);
  }
  
  public SiriConnectionException(String message, Throwable cause) {
    super(message,cause);
  }
}
