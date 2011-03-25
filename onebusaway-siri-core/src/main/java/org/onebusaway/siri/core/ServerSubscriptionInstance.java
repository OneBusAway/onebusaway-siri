package org.onebusaway.siri.core;

import java.util.List;

import org.onebusaway.siri.core.filters.SiriModuleDeliveryFilter;

import uk.org.siri.siri.AbstractSubscriptionStructure;

class ServerSubscriptionInstance {

  private final ServerSubscriptionChannel channel;

  private final ESiriModuleType moduleType;

  private final String subscriptionId;

  private final String messageId;

  private final AbstractSubscriptionStructure moduleSubscription;

  private final List<SiriModuleDeliveryFilter> filters;

  public ServerSubscriptionInstance(ServerSubscriptionChannel channel,
      ESiriModuleType moduleType, String subscriptionId, String messageId,
      AbstractSubscriptionStructure moduleSubscription,
      List<SiriModuleDeliveryFilter> filters) {
    this.channel = channel;
    this.moduleType = moduleType;
    this.subscriptionId = subscriptionId;
    this.messageId = messageId;
    this.moduleSubscription = moduleSubscription;
    this.filters = filters;
  }

  public ServerSubscriptionInstanceId getId() {
    return new ServerSubscriptionInstanceId(channel.getId(), subscriptionId);
  }

  public ServerSubscriptionChannel getChannel() {
    return channel;
  }

  public ESiriModuleType getModuleType() {
    return moduleType;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  public String getMessageId() {
    return messageId;
  }

  public AbstractSubscriptionStructure getModuleSubscription() {
    return moduleSubscription;
  }

  public List<SiriModuleDeliveryFilter> getFilters() {
    return filters;
  }

  @Override
  public String toString() {
    ServerSubscriptionChannelId channelId = channel.getId();
    return "SubscriptionInstance(subscriptionId=" + channelId.getSubscriberId()
        + " consumerAddress=" + channelId.getAddress() + " subId="
        + subscriptionId + ")";
  }

}