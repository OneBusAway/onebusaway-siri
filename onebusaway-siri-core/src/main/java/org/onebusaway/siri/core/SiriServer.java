package org.onebusaway.siri.core;

import java.io.Writer;
import java.util.Date;
import java.util.UUID;

import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;

public class SiriServer extends SiriCommon {

  private String _identity;

  public SiriServer() {

    _identity = UUID.randomUUID().toString();
  }

  public void setIdentity(String identity) {
    _identity = identity;
  }

  public void deliverResponse(ServiceRequest request, ServiceDelivery delivery,
      Writer writer) {

    ParticipantRefStructure identity = new ParticipantRefStructure();
    identity.setValue(_identity);
    delivery.setProducerRef(identity);

    delivery.setAddress(null);

    delivery.setResponseTimestamp(new Date());

    marshall(delivery, writer);
  }
}
