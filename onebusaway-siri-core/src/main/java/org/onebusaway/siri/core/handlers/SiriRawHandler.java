package org.onebusaway.siri.core.handlers;

import java.io.Reader;
import java.io.Writer;

public interface SiriRawHandler {

  public void handleRawRequest(Reader reader, Writer writer);
}
