package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ObjectUtils;
import org.onebusaway.collections.PropertyPathExpression;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractServiceRequestStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

/**
 * SIRI utility functions
 * 
 * @author bdferris
 */
public class SiriLibrary {

  @SuppressWarnings("unchecked")
  public static <T extends AbstractServiceRequestStructure> List<T> getServiceRequestsForModule(
      ServiceRequest serviceRequest, ESiriModuleType moduleType) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        return (List<T>) serviceRequest.getVehicleMonitoringRequest();
      default:
        return new ArrayList<T>();
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends AbstractSubscriptionStructure> List<T> getSubscriptionRequestsForModule(
      SubscriptionRequest subscriptionRequest, ESiriModuleType moduleType) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        return (List<T>) subscriptionRequest.getVehicleMonitoringSubscriptionRequest();
      default:
        return new ArrayList<T>();
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends AbstractServiceDeliveryStructure> List<T> getServiceDeliveriesForModule(
      ServiceDelivery serviceDelivery, ESiriModuleType moduleType) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        return (List<T>) serviceDelivery.getVehicleMonitoringDelivery();
      default:
        return new ArrayList<T>();
    }
  }

  /****
   * 
   ****/

  public static <T> List<T> grep(Iterable<T> elements,
      String propertyPathExpression, Object equalityValue) {

    PropertyPathExpression ppe = new PropertyPathExpression(
        propertyPathExpression);
    List<T> matches = new ArrayList<T>();

    for (T element : elements) {
      Object value = ppe.invoke(element);
      if (ObjectUtils.equals(value, equalityValue))
        matches.add(element);
    }

    return matches;
  }

  /****
   * Deep Copy Methods
   ****/

  public static VehicleMonitoringDeliveryStructure copyVehicleMonitoring(
      VehicleMonitoringDeliveryStructure from,
      VehicleMonitoringDeliveryStructure to) {

    copyServiceDelivery(from, to);

    copyList(from.getVehicleActivity(), to.getVehicleActivity());
    copyList(from.getVehicleActivityCancellation(),
        to.getVehicleActivityCancellation());
    copyList(from.getVehicleActivityNote(), to.getVehicleActivityNote());

    return to;
  }

  public static AbstractServiceDeliveryStructure copyServiceDelivery(
      ESiriModuleType moduleType, AbstractServiceDeliveryStructure from) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        return copyVehicleMonitoring((VehicleMonitoringDeliveryStructure) from,
            new VehicleMonitoringDeliveryStructure());
      default:
        throw new UnsupportedOperationException();
    }
  }

  public static void copyServiceDelivery(AbstractServiceDeliveryStructure from,
      AbstractServiceDeliveryStructure to) {

    to.setDefaultLanguage(from.getDefaultLanguage());
    to.setErrorCondition(from.getErrorCondition());
    to.setRequestMessageRef(from.getRequestMessageRef());
    to.setResponseTimestamp(from.getResponseTimestamp());
    to.setShortestPossibleCycle(from.getShortestPossibleCycle());
    to.setStatus(from.isStatus());
    to.setSubscriberRef(from.getSubscriberRef());
    to.setSubscriptionFilterRef(from.getSubscriptionFilterRef());
    to.setSubscriptionRef(from.getSubscriptionRef());
    to.setValidUntil(from.getValidUntil());
  }

  public static <T> void copyList(List<T> from, List<T> to) {
    to.clear();
    to.addAll(from);
  }

}
