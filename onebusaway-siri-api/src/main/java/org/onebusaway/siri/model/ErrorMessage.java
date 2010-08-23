package org.onebusaway.siri.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Siri doesn't have much of a concept of error messages. Because we anticipate
 * farily minimal error reporting requirements in the API, we'll just use this
 * simple class.
 * 
 */
@XStreamAlias("ErrorMessage")
public class ErrorMessage {

  public String message;

  public ErrorMessage(String message) {
    this.message = message;
  }

}
