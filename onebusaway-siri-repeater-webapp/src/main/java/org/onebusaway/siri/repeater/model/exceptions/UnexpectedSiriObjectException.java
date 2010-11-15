package org.onebusaway.siri.repeater.model.exceptions;

public class UnexpectedSiriObjectException extends SiriRepeaterException {

  private static final long serialVersionUID = 1L;

  private Object _object;

  public UnexpectedSiriObjectException(Object object) {
    _object = object;
  }

  public Object getObject() {
    return _object;
  }
}
