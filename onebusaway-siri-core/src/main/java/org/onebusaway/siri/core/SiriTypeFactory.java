package org.onebusaway.siri.core;

import java.util.UUID;

import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;

public class SiriTypeFactory {

  public static ParticipantRefStructure particpantRef(String value) {
    ParticipantRefStructure ref = new ParticipantRefStructure();
    ref.setValue(value);
    return ref;
  }

  public static MessageQualifierStructure messageId(String value) {
    MessageQualifierStructure messageId = new MessageQualifierStructure();
    messageId.setValue(value);
    return messageId;
  }

  public static MessageQualifierStructure randomMessageId() {
    return messageId(UUID.randomUUID().toString());
  }

  public static SubscriptionQualifierStructure subscriptionId(
      String subscriptionId) {
    SubscriptionQualifierStructure s = new SubscriptionQualifierStructure();
    s.setValue(subscriptionId);
    return s;
  }

  public static SubscriptionQualifierStructure randomSubscriptionId() {
    return subscriptionId(UUID.randomUUID().toString());
  }
}
