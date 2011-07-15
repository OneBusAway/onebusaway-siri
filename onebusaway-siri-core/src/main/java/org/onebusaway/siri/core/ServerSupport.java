package org.onebusaway.siri.core;

import java.util.List;

import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri.UnknownSubscriberErrorStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;

public class ServerSupport {

  public String getSubscriberIdForTerminateSubscriptionRequest(
      TerminateSubscriptionRequestStructure request) {
    String subscriberId = null;

    ParticipantRefStructure requestorRef = request.getRequestorRef();
    if (requestorRef != null && requestorRef.getValue() != null)
      subscriberId = requestorRef.getValue();

    ParticipantRefStructure subscriberRef = request.getSubscriberRef();
    if (subscriberRef != null && subscriberRef.getValue() != null)
      subscriberId = subscriberRef.getValue();
    return subscriberId;
  }

  public void addTerminateSubscriptionStatusForMissingSubscriberRef(
      TerminateSubscriptionRequestStructure request,
      TerminateSubscriptionResponseStructure response) {

    List<TerminationResponseStatus> statuses = response.getTerminationResponseStatus();

    TerminationResponseStatus status = new TerminationResponseStatus();
    status.setStatus(false);
    status.setRequestMessageRef(request.getMessageIdentifier());

    TerminationResponseStatus.ErrorCondition condition = new TerminationResponseStatus.ErrorCondition();
    condition.setDescription(SiriTypeFactory.errorDescription("Missing SubscriberRef"));

    UnknownSubscriberErrorStructure error = new UnknownSubscriberErrorStructure();
    error.setErrorText("Missing SubscriberRef");

    condition.setUnknownSubscriberError(error);

    status.setErrorCondition(condition);

    statuses.add(status);
  }
}
