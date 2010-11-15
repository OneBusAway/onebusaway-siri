package org.onebusaway.siri.repeater.impl;

import java.net.URI;
import java.net.URISyntaxException;

import org.onebusaway.siri.repeater.model.exceptions.InvalidSubscriptionAddressSiriException;

public class ValueLibrary {
  public static final boolean isSet(String value) {
    return value != null && !value.trim().isEmpty();
  }

  public static final URI getAddressAsUri(String address)
      throws InvalidSubscriptionAddressSiriException {

    try {
      address = address.trim();
      return new URI(address);
    } catch (URISyntaxException ex) {
      throw new InvalidSubscriptionAddressSiriException(address, ex);
    }
  }
}
