package org.onebusaway.siri.repeater.model.exceptions;

public class InvalidSubscriptionAddressSiriException extends
    SiriRepeaterException {

  private static final long serialVersionUID = 1L;

  private String _address;

  public InvalidSubscriptionAddressSiriException(String address, Throwable ex) {
    super("Invalid subscription address: " + address, ex);
    _address = address;
  }

  public String getAddress() {
    return _address;
  }
}
