/**
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.core.services;

import javax.inject.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.onebusaway.siri.core.exceptions.SiriSerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JAXBContextProvider implements Provider<JAXBContext> {

  private static Logger _log = LoggerFactory.getLogger(JAXBContextProvider.class);

  private JAXBContext _jaxbContext;

  @Override
  public synchronized JAXBContext get() {
    if (_jaxbContext == null) {
      try {
        _log.info("Loading JAXB context.  This may take a few seconds...");
        _jaxbContext = JAXBContext.newInstance("uk.org.siri:org.onebusaway.siri:uk.org.siri.siri");
        _log.info("Loading JAXB context complete.");
      } catch (JAXBException ex) {
        throw new SiriSerializationException(ex);
      }
    }
    return _jaxbContext;
  }
}
