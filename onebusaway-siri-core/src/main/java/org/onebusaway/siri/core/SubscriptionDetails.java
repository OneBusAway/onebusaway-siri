package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.List;

import uk.org.siri.siri.SubscriptionRequest;

class SubscriptionDetails {

  private final String address;

  private final String messageId;

  private final SubscriptionRequest subscriptionRequest;

  private List<ModuleSubscriptionDetails> moduleDetails = new ArrayList<ModuleSubscriptionDetails>();

  public SubscriptionDetails(String address, String messageId,
      SubscriptionRequest subscriptionRequest) {
    this.address = address;
    this.messageId = messageId;
    this.subscriptionRequest = subscriptionRequest;
  }

  public String getAddress() {
    return address;
  }

  public String getMessageId() {
    return messageId;
  }

  public SubscriptionRequest getSubscriptionRequest() {
    return subscriptionRequest;
  }

  public List<ModuleSubscriptionDetails> getModuleDetails() {
    return moduleDetails;
  }

  public void addModuleDetails(ModuleSubscriptionDetails details) {
    moduleDetails.add(details);
  }
}