package org.onebusaway.siri.core.filters;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

/**
 * An interface to filter the individual
 * {@link AbstractServiceDeliveryStructure} elements that are carried in a
 * {@link ServiceDelivery} container.
 * 
 * @author bdferris
 * @see SiriModuleDeliveryFilterFactory
 */
public interface SiriModuleDeliveryFilter {

  /**
   * A delivery filter allows you to modify the contents of a
   * {@link AbstractServiceDeliveryStructure}, as contained within a
   * {@link ServiceDelivery} payload, before it is sent to a client.
   * 
   * Your filter implementation can return the input delivery object, perhaps
   * modified in some way. It can also return an entirely new delivery object or
   * null to indicate no result should be published to the client.
   * 
   * When modifying the delivery object directly, your filter implementation
   * must adhere to the following behavior:
   * 
   * The filter is free to modify the delivery object directly, changing field
   * values or modifying first level result lists (for example, adding or
   * removing items from
   * {@link VehicleMonitoringDeliveryStructure#getVehicleActivity()}. However,
   * if you wish to transform the second-level objects (other than adding or
   * removing them from the parent delivery object list), YOU MUST MAKE COPIES,
   * which you can then modify and include in the filtered delivery result.
   * 
   * The idea is that for performance reasons, we make a shallow copy of the
   * incoming delivery object for filter to work with (as opposed to a
   * deep-copy), leaving the second-level objects unaltered. Since most filters
   * will simply be removing these objects from the delivery object, this
   * approach is appropriate for the majority of use-cases. However, if your
   * filter needs to modify or transform the second-level objects, you need to
   * make copies of those objects. Otherwise, your modifications will affect the
   * output of other unrelated filter chains (aka bad).
   * 
   * If you want to exclude all output in the filter, simply return null.
   * 
   * @param delivery the parent ServiceDelivery object, for your reference
   * @param moduleDelivery the module delivery structure to filter
   * 
   * @return the updated delivery structure or null for empty result
   */
  public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
      AbstractServiceDeliveryStructure moduleDelivery);
}
