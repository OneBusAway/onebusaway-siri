package org.onebusaway.siri.repeater;

import java.util.List;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;
import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilterSource;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriModuleDeliveryFilterMatch implements
    SiriModuleDeliveryFilterSource {

  private ESiriModuleType moduleType;

  private String participant;

  private SiriModuleDeliveryFilter filter;

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public void setModuleType(ESiriModuleType moduleType) {
    this.moduleType = moduleType;
  }

  public void setParticipant(String participant) {
    this.participant = participant;
  }

  public void setFilter(SiriModuleDeliveryFilter filter) {
    this.filter = filter;
  }

  @Override
  public void addFiltersForModuleSubscription(
      SubscriptionRequest subscriptionRequest, ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleSubscriptionRequest,
      List<SiriModuleDeliveryFilter> filters) {

    /**
     * No point if we don't have a filter
     */
    if (filter == null)
      return;

    /**
     * Does the module type match?
     */
    if (this.moduleType != null && this.moduleType != moduleType)
      return;

    if (this.participant != null) {

      ParticipantRefStructure participantRef = moduleSubscriptionRequest.getSubscriberRef();

      if (participantRef == null || participantRef.getValue() == null)
        participantRef = subscriptionRequest.getRequestorRef();

      if (participantRef == null || participantRef.getValue() == null)
        return;

      String participantId = participantRef.getValue();
      if (!participantId.matches(this.participant))
        return;
    }

    filters.add(filter);
  }
}
