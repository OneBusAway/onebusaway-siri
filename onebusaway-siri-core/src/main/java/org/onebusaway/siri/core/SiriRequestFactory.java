package org.onebusaway.siri.core;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import uk.org.siri.siri.AbstractServiceRequestStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.DirectionRefStructure;
import uk.org.siri.siri.EstimatedTimetableRequestStructure;
import uk.org.siri.siri.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri.LineRefStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ProductionTimetableRequestStructure;
import uk.org.siri.siri.ProductionTimetableSubscriptionRequest;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.StopMonitoringRequestStructure;
import uk.org.siri.siri.StopMonitoringSubscriptionStructure;
import uk.org.siri.siri.StopTimetableRequestStructure;
import uk.org.siri.siri.StopTimetableSubscriptionStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.VehicleMonitoringRefStructure;
import uk.org.siri.siri.VehicleMonitoringRequestStructure;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;
import uk.org.siri.siri.VehicleRefStructure;

public class SiriRequestFactory {

  private static final String ARG_MODULE_TYPE = "ModuleType";
  private static final String ARG_MESSAGE_IDENTIFIER = "MessageIdentifier";
  private static final String ARG_MAXIMUM_VEHICLES = "MaximumVehicles";
  private static final String ARG_VEHICLE_REF = "VehicleRef";
  private static final String ARG_LINE_REF = "LineRef";
  private static final String ARG_DIRECTION_REF = "DirectionRef";
  private static final String ARG_VEHICLE_MONITORING_REF = "VehicleMonitoringRef";

  public ServiceRequest createServiceRequest(Map<String, String> args) {

    ServiceRequest request = new ServiceRequest();

    String messageIdentifierValue = args.get(ARG_MESSAGE_IDENTIFIER);
    if (messageIdentifierValue != null) {
      MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
      messageIdentifier.setValue(messageIdentifierValue);
      request.setMessageIdentifier(messageIdentifier);
    }

    String moduleTypeValue = args.get(ARG_MODULE_TYPE);

    if (moduleTypeValue != null) {

      ESiriModuleType moduleType = ESiriModuleType.valueOf(moduleTypeValue.toUpperCase());
      AbstractServiceRequestStructure moduleRequest = createServiceRequestForModuleType(moduleType);

      handleModuleServiceRequestSpecificArguments(moduleType, moduleRequest,
          args);

      List<AbstractServiceRequestStructure> moduleRequests = SiriLibrary.getServiceRequestsForModule(
          request, moduleType);
      moduleRequests.add(moduleRequest);

    }
    return request;
  }

  public SubscriptionRequest createSubscriptionRequest(Map<String, String> args) {

    SubscriptionRequest request = new SubscriptionRequest();

    String messageIdentifierValue = args.get(ARG_MESSAGE_IDENTIFIER);
    if (messageIdentifierValue != null) {
      MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
      messageIdentifier.setValue(messageIdentifierValue);
      request.setMessageIdentifier(messageIdentifier);
    }

    String moduleTypeValue = args.get(ARG_MODULE_TYPE);

    if (moduleTypeValue != null) {

      ESiriModuleType moduleType = ESiriModuleType.valueOf(moduleTypeValue.toUpperCase());
      AbstractSubscriptionStructure moduleSubscription = createSubscriptionForModuleType(moduleType);

      String subscriptionIdentifierValue = args.get("SubscriptionIdentifier");
      if (subscriptionIdentifierValue != null) {
        SubscriptionQualifierStructure value = new SubscriptionQualifierStructure();
        value.setValue(subscriptionIdentifierValue);
        moduleSubscription.setSubscriptionIdentifier(value);
      }

      handleModuleSubscriptionSpecificArguments(moduleType, moduleSubscription,
          args);

      List<AbstractSubscriptionStructure> moduleSubscriptions = SiriLibrary.getSubscriptionRequestsForModule(
          request, moduleType);
      moduleSubscriptions.add(moduleSubscription);

    }
    return request;
  }

  /****
   * 
   * @param moduleType
   * @return
   ****/

  private AbstractServiceRequestStructure createServiceRequestForModuleType(
      ESiriModuleType moduleType) {

    switch (moduleType) {
      case PRODUCTION_TIMETABLE:
        return new ProductionTimetableRequestStructure();
      case ESTIMATED_TIMETABLE:
        return new EstimatedTimetableRequestStructure();
      case STOP_TIMETABLE:
        return new StopTimetableRequestStructure();
      case STOP_MONITORING:
        return new StopMonitoringRequestStructure();
      case VEHICLE_MONITORING:
        return new VehicleMonitoringRequestStructure();
      default:
        throw new UnsupportedOperationException();
    }
  }

  private AbstractSubscriptionStructure createSubscriptionForModuleType(
      ESiriModuleType moduleType) {

    switch (moduleType) {
      case PRODUCTION_TIMETABLE:
        return new ProductionTimetableSubscriptionRequest();
      case ESTIMATED_TIMETABLE:
        return new EstimatedTimetableSubscriptionStructure();
      case STOP_TIMETABLE:
        return new StopTimetableSubscriptionStructure();
      case STOP_MONITORING:
        return new StopMonitoringSubscriptionStructure();
      case VEHICLE_MONITORING:
        return new VehicleMonitoringSubscriptionStructure();
      default:
        throw new UnsupportedOperationException();
    }
  }

  private void handleModuleServiceRequestSpecificArguments(
      ESiriModuleType moduleType,
      AbstractServiceRequestStructure moduleServiceRequest,
      Map<String, String> args) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        applyArgsToVehicleMonitoringRequest(
            (VehicleMonitoringRequestStructure) moduleServiceRequest, args);
        break;
    }
  }

  private void handleModuleSubscriptionSpecificArguments(
      ESiriModuleType moduleType,
      AbstractSubscriptionStructure moduleSubscription, Map<String, String> args) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        handleVehicleMonitoringSubscriptionSpecificArguments(
            (VehicleMonitoringSubscriptionStructure) moduleSubscription, args);
        break;
    }
  }

  private void handleVehicleMonitoringSubscriptionSpecificArguments(
      VehicleMonitoringSubscriptionStructure moduleSubscription,
      Map<String, String> args) {

    VehicleMonitoringRequestStructure vmr = new VehicleMonitoringRequestStructure();
    moduleSubscription.setVehicleMonitoringRequest(vmr);

    applyArgsToVehicleMonitoringRequest(vmr, args);
  }

  private void applyArgsToVehicleMonitoringRequest(
      VehicleMonitoringRequestStructure vmr, Map<String, String> args) {
    String vehicleMonitoringRefValue = args.get(ARG_VEHICLE_MONITORING_REF);
    if (vehicleMonitoringRefValue != null) {
      VehicleMonitoringRefStructure vehicleMonitoringRef = new VehicleMonitoringRefStructure();
      vehicleMonitoringRef.setValue(vehicleMonitoringRefValue);
      vmr.setVehicleMonitoringRef(vehicleMonitoringRef);
    }

    String directionRefValue = args.get(ARG_DIRECTION_REF);
    if (directionRefValue != null) {
      DirectionRefStructure directionRef = new DirectionRefStructure();
      directionRef.setValue(directionRefValue);
      vmr.setDirectionRef(directionRef);
    }

    String lineRefValue = args.get(ARG_LINE_REF);
    if (lineRefValue != null) {
      LineRefStructure lineRef = new LineRefStructure();
      lineRef.setValue(lineRefValue);
      vmr.setLineRef(lineRef);
    }

    String vehicleRefValue = args.get(ARG_VEHICLE_REF);
    if (vehicleRefValue != null) {
      VehicleRefStructure vehicleRef = new VehicleRefStructure();
      vehicleRef.setValue(vehicleRefValue);
      vmr.setVehicleRef(vehicleRef);
    }

    String maximumVehiclesValue = args.get(ARG_MAXIMUM_VEHICLES);
    if (maximumVehiclesValue != null) {
      vmr.setMaximumVehicles(new BigInteger(maximumVehiclesValue));
    }
  }
}
