/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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
package org.onebusaway.siri.core.subscriptions.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;

import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.OtherErrorStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.ServiceDeliveryErrorConditionStructure;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.TerminateSubscriptionResponseStructure.TerminationResponseStatus;
import uk.org.siri.siri.UnknownSubscriberErrorStructure;
import uk.org.siri.siri.UnknownSubscriptionErrorStructure;

class ServerSupport {

  public String getSubscriberIdForSubscriptionRequest(
      SubscriptionRequest subscriptionRequest,
      AbstractSubscriptionStructure moduleRequest) {

    ParticipantRefStructure subscriberRef = moduleRequest.getSubscriberRef();
    if (subscriberRef != null && subscriberRef.getValue() != null)
      return subscriberRef.getValue();

    subscriberRef = subscriptionRequest.getRequestorRef();
    if (subscriberRef != null && subscriberRef.getValue() != null)
      return subscriberRef.getValue();

    return null;
  }

  public String getSubscriberIdForTerminateSubscriptionRequest(
      TerminateSubscriptionRequestStructure request) {
    ParticipantRefStructure ref = request.getSubscriberRef();
    if (ref != null && ref.getValue() != null)
      return ref.getValue();
    ref = request.getRequestorRef();
    if (ref != null && ref.getValue() != null)
      return ref.getValue();
    return null;
  }

  public StatusResponseStructure getStatusResponse(SubscriptionRequest request,
      SubscriptionId id) {

    StatusResponseStructure status = new StatusResponseStructure();

    status.setRequestMessageRef(request.getMessageIdentifier());
    status.setResponseTimestamp(new Date());
    status.setStatus(Boolean.TRUE);
    if (id != null) {
      status.setSubscriberRef(SiriTypeFactory.particpantRef(id.getSubscriberId()));
      status.setSubscriptionRef(SiriTypeFactory.subscriptionId(id.getSubscriptionId()));
    }

    return status;
  }

  public StatusResponseStructure getStatusResponseWithErrorMessage(
      SubscriptionRequest request, String errorText,
      SubscriptionId subscriptionId) {

    StatusResponseStructure status = getStatusResponse(request, null);

    status.setStatus(Boolean.FALSE);

    ServiceDeliveryErrorConditionStructure error = new ServiceDeliveryErrorConditionStructure();
    error.setDescription(SiriTypeFactory.errorDescription(errorText));

    OtherErrorStructure otherError = new OtherErrorStructure();
    otherError.setErrorText(errorText);
    error.setOtherError(otherError);

    status.setErrorCondition(error);

    return status;
  }

  public void addTerminateSubscriptionStatusForMissingSubscriberRef(
      TerminateSubscriptionRequestStructure request,
      List<TerminationResponseStatus> statuses) {

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

  public void setTerminationResponseErrorConditionWithUnknownSubscription(
      TerminationResponseStatus status) {

    status.setStatus(Boolean.FALSE);

    String errorMessage = "Unknown subscription";

    TerminationResponseStatus.ErrorCondition condition = new TerminationResponseStatus.ErrorCondition();
    status.setErrorCondition(condition);
    condition.setDescription(SiriTypeFactory.errorDescription(errorMessage));

    UnknownSubscriptionErrorStructure error = new UnknownSubscriptionErrorStructure();
    error.setErrorText(errorMessage);
    condition.setUnknownSubscriptionError(error);
  }

  public void setTerminationResponseErrorConditionWithException(
      TerminationResponseStatus status, Throwable ex) {

    status.setStatus(Boolean.FALSE);

    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);

    writer.append("Exception thropw on subscription termination:\n");
    ex.printStackTrace(printWriter);

    String errorMessage = writer.toString();

    TerminationResponseStatus.ErrorCondition condition = new TerminationResponseStatus.ErrorCondition();
    status.setErrorCondition(condition);
    condition.setDescription(SiriTypeFactory.errorDescription(errorMessage));

    OtherErrorStructure error = new OtherErrorStructure();
    error.setErrorText(errorMessage);
    condition.setOtherError(error);
  }
}
