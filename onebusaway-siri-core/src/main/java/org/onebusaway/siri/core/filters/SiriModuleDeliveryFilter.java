package org.onebusaway.siri.core.filters;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;

/**
 * An interface to filter the individual
 * {@link AbstractServiceDeliveryStructure} elements that are carried in a
 * {@link ServiceDelivery} container.
 * 
 * @author bdferris
 */
public interface SiriModuleDeliveryFilter {

  /**
   * <p>
   * A delivery filter allows you to modify the contents of a
   * {@link AbstractServiceDeliveryStructure}, as contained within a
   * {@link ServiceDelivery} payload, before it is sent to a client.
   * </p>
   * 
   * <p>
   * Your filter implementation can return the input delivery object, perhaps
   * modified in some way. It can also return an entirely new delivery object or
   * null to indicate no result should be published to the client.
   * </p>
   * 
   * @param delivery the parent ServiceDelivery object, for your reference
   * @param moduleDelivery the module delivery structure to filter
   * 
   * @return the updated delivery structure or null for empty result
   */
  public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
      AbstractServiceDeliveryStructure moduleDelivery);
}
