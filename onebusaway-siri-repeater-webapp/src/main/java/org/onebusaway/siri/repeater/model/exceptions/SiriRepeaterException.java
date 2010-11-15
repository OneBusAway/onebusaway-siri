package org.onebusaway.siri.repeater.model.exceptions;

public class SiriRepeaterException extends Exception {

  private static final long serialVersionUID = 1L;

  public SiriRepeaterException() {

  }

  public SiriRepeaterException(String message, Throwable ex) {
    super(message, ex);
  }
}
