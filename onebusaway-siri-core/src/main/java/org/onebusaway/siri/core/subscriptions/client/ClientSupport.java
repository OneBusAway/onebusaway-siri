package org.onebusaway.siri.core.subscriptions.client;

import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.exceptions.SiriMissingArgumentException;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ErrorCodeStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;

/**
 * This is a support class for {@link SiriClient} so we can off-load some
 * support methods.
 * 
 * @author bdferris
 * 
 */
class ClientSupport {

  public static SubscriptionId getSubscriptionId(
      ParticipantRefStructure subscriberRef,
      SubscriptionQualifierStructure subscriptionRef) {

    if (subscriberRef == null || subscriberRef.getValue() == null)
      throw new SiriMissingArgumentException("SubscriberRef");

    if (subscriptionRef == null || subscriptionRef.getValue() == null)
      throw new SiriMissingArgumentException("SubscriptionRef");

    return new SubscriptionId(subscriberRef.getValue(),
        subscriptionRef.getValue());
  }

  public static void appendError(ErrorCodeStructure code, StringBuilder b) {
    if (code == null)
      return;
    Class<? extends ErrorCodeStructure> clazz = code.getClass();
    String name = clazz.getName();
    int index = name.lastIndexOf('.');
    if (index != -1)
      name = name.substring(index + 1);
    b.append(" errorType=").append(name);
    if (code.getErrorText() != null)
      b.append(" errorText=").append(code.getErrorText());
  }

  public static SubscriptionId getSubscriptionIdForModuleDelivery(
      AbstractServiceDeliveryStructure moduleDelivery) {
  
    ParticipantRefStructure subscriberRef = moduleDelivery.getSubscriberRef();
    SubscriptionQualifierStructure subscriptionRef = moduleDelivery.getSubscriptionRef();
  
    return getSubscriptionId(subscriberRef, subscriptionRef);
  }

}
