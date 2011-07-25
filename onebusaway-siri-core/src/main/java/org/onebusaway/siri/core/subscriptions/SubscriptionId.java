package org.onebusaway.siri.core.subscriptions;

import org.onebusaway.siri.core.ESiriModuleType;

import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;

/**
 * According to the SIRI spec, the combination of subscriber id, functional
 * service area (equivalent to our {@link ESiriModuleType}), and the
 * subscription id uniquely identify a subscription. That said, a number of key
 * subscription-management structures (include {@link StatusResponseStructure}
 * and {@link TerminateSubscriptionRequestStructure} don't have a mechanism for
 * specifying the functional service area. So, for our purposes of our library,
 * subscriptions will be uniquely identified by the combination of subscriber id
 * and subscription id. If you attempt to create two subscriptions with the same
 * subscriberId and subscriptionId, but different functional service areas, we
 * will throw an error.
 * 
 * @author bdferris
 */
public class SubscriptionId {

  private final String subscriberId;

  private final String subscriptionId;

  public SubscriptionId(String subscriberId, String subscriptionId) {
    if (subscriberId == null)
      throw new IllegalArgumentException("subscriberId is null");
    if (subscriptionId == null)
      throw new IllegalArgumentException("subscriptionId is null");
    this.subscriberId = subscriberId;
    this.subscriptionId = subscriptionId;
  }

  public String getSubscriberId() {
    return subscriberId;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + subscriberId.hashCode();
    result = prime * result + subscriptionId.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SubscriptionId other = (SubscriptionId) obj;
    if (!subscriberId.equals(other.subscriberId))
      return false;
    if (!subscriptionId.equals(other.subscriptionId))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "subscriberId=" + subscriberId + " subscriptionId=" + subscriptionId;
  }
}
