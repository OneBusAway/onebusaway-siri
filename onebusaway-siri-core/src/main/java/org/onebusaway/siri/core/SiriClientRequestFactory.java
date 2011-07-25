package org.onebusaway.siri.core;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.exceptions.SiriMissingArgumentException;
import org.onebusaway.siri.core.exceptions.SiriUnknownVersionException;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.AbstractServiceRequestStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.CheckStatusRequestStructure;
import uk.org.siri.siri.DirectionRefStructure;
import uk.org.siri.siri.EstimatedTimetableRequestStructure;
import uk.org.siri.siri.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri.LineRefStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ProductionTimetableRequestStructure;
import uk.org.siri.siri.ProductionTimetableSubscriptionRequest;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SituationExchangeRequestStructure;
import uk.org.siri.siri.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri.StopMonitoringRequestStructure;
import uk.org.siri.siri.StopMonitoringSubscriptionStructure;
import uk.org.siri.siri.StopTimetableRequestStructure;
import uk.org.siri.siri.StopTimetableSubscriptionStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri.VehicleMonitoringRefStructure;
import uk.org.siri.siri.VehicleMonitoringRequestStructure;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;
import uk.org.siri.siri.VehicleRefStructure;

public class SiriClientRequestFactory {

  private static final String ARG_URL = "Url";
  private static final String ARG_VERSION = "Version";
  private static final String ARG_MODULE_TYPE = "ModuleType";
  private static final String ARG_RECONNECTION_ATTEMPTS = "ReconnectionAttempts";
  private static final String ARG_RECONNECTION_INTERVAL = "ReconnectionInterval";

  private static final String ARG_HEARTBEAT_INTERVAL = "HeartbeatInterval";
  private static final String ARG_CHECK_STATUS_INTERVAL = "CheckStatusInterval";
  private static final String ARG_INITIAL_TERMINATION_TIME = "InitialTerminationTime";

  private static final String ARG_MESSAGE_IDENTIFIER = "MessageIdentifier";
  private static final String ARG_MAXIMUM_VEHICLES = "MaximumVehicles";
  private static final String ARG_VEHICLE_REF = "VehicleRef";
  private static final String ARG_LINE_REF = "LineRef";
  private static final String ARG_DIRECTION_REF = "DirectionRef";
  private static final String ARG_VEHICLE_MONITORING_REF = "VehicleMonitoringRef";

  private static final DatatypeFactory _dataTypeFactory = createDataTypeFactory();

  public SiriClientRequest createServiceRequest(Map<String, String> args) {

    SiriClientRequest request = new SiriClientRequest();

    processCommonArgs(args, request);

    ServiceRequest serviceRequest = new ServiceRequest();
    Siri payload = new Siri();
    payload.setServiceRequest(serviceRequest);
    request.setPayload(payload);

    String messageIdentifierValue = args.get(ARG_MESSAGE_IDENTIFIER);
    if (messageIdentifierValue != null) {
      MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
      messageIdentifier.setValue(messageIdentifierValue);
      serviceRequest.setMessageIdentifier(messageIdentifier);
    }

    String moduleTypeValue = args.get(ARG_MODULE_TYPE);

    if (moduleTypeValue != null) {

      ESiriModuleType moduleType = ESiriModuleType.valueOf(moduleTypeValue.toUpperCase());
      AbstractServiceRequestStructure moduleRequest = createServiceRequestForModuleType(moduleType);

      handleModuleServiceRequestSpecificArguments(moduleType, moduleRequest,
          args);

      List<AbstractServiceRequestStructure> moduleRequests = SiriLibrary.getServiceRequestsForModule(
          serviceRequest, moduleType);
      moduleRequests.add(moduleRequest);

    }

    return request;
  }

  public SiriClientRequest createSubscriptionRequest(Map<String, String> args) {

    SiriClientRequest request = new SiriClientRequest();
    processCommonArgs(args, request);

    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    Siri payload = new Siri();
    payload.setSubscriptionRequest(subscriptionRequest);
    request.setPayload(payload);

    String messageIdentifierValue = args.get(ARG_MESSAGE_IDENTIFIER);
    if (messageIdentifierValue != null) {
      MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
      messageIdentifier.setValue(messageIdentifierValue);
      subscriptionRequest.setMessageIdentifier(messageIdentifier);
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
          subscriptionRequest, moduleType);
      moduleSubscriptions.add(moduleSubscription);

    }

    return request;
  }
  
  public SiriClientRequest createCheckStatusRequest(
      Map<String, String> args) {

    SiriClientRequest request = new SiriClientRequest();
    processCommonArgs(args, request);

    CheckStatusRequestStructure checkStatusRequest = new CheckStatusRequestStructure();

    Siri payload = new Siri();
    payload.setCheckStatusRequest(checkStatusRequest);
    request.setPayload(payload);
    
    return request;
  }

  public SiriClientRequest createTerminateSubscriptionRequest(
      Map<String, String> args) {

    SiriClientRequest request = new SiriClientRequest();
    processCommonArgs(args, request);

    TerminateSubscriptionRequestStructure terminateRequest = new TerminateSubscriptionRequestStructure();
    Siri payload = new Siri();
    payload.setTerminateSubscriptionRequest(terminateRequest);
    request.setPayload(payload);

    String messageIdentifierValue = args.get(ARG_MESSAGE_IDENTIFIER);
    if (messageIdentifierValue != null) {
      MessageQualifierStructure messageIdentifier = new MessageQualifierStructure();
      messageIdentifier.setValue(messageIdentifierValue);
      terminateRequest.setMessageIdentifier(messageIdentifier);
    }

    String subscriptionIdentifierValue = args.get("SubscriptionIdentifier");
    if (subscriptionIdentifierValue != null) {
      SubscriptionQualifierStructure value = new SubscriptionQualifierStructure();
      value.setValue(subscriptionIdentifierValue);
      terminateRequest.getSubscriptionRef().add(value);
    } else {
      terminateRequest.setAll("true");
    }

    return request;
  }

  /****
   * Private Methods
   ****/

  private void processCommonArgs(Map<String, String> args,
      SiriClientRequest request) {

    String url = args.get(ARG_URL);
    if (url == null)
      throw new SiriMissingArgumentException(ARG_URL);
    request.setTargetUrl(url);

    String versionId = args.get(ARG_VERSION);
    if (versionId != null) {
      ESiriVersion version = ESiriVersion.getVersionForVersionId(versionId);
      if (version == null) {
        throw new SiriUnknownVersionException(versionId);
      }
      request.setTargetVersion(version);
    }

    String initialTerminationTime = args.get(ARG_INITIAL_TERMINATION_TIME);
    if (initialTerminationTime != null) {
      if (initialTerminationTime.startsWith("P")) {
        Duration duration = _dataTypeFactory.newDuration(initialTerminationTime);
        long time = duration.getTimeInMillis(new Date());
        request.setInitialTerminationTime(new Date(time));
      } else {
        try {
          Date time = getIso8601StringAsTime(initialTerminationTime,
              TimeZone.getDefault());
          request.setInitialTerminationTime(time);
        } catch (ParseException e) {
          throw new SiriException(
              "error parsing initial termination time (ISO 8601)");
        }
      }
    } else {
      /**
       * By default, expire in 24 hours
       */
      Calendar c = Calendar.getInstance();
      c.add(Calendar.DAY_OF_YEAR, 1);
      request.setInitialTerminationTime(c.getTime());
    }

    String reconnectionAttempts = args.get(ARG_RECONNECTION_ATTEMPTS);
    if (reconnectionAttempts != null) {
      int attempts = Integer.parseInt(reconnectionAttempts);
      request.setReconnectionAttempts(attempts);
    }

    String reconnectionInterval = args.get(ARG_RECONNECTION_INTERVAL);
    if (reconnectionInterval != null) {
      int interval = Integer.parseInt(reconnectionInterval);
      request.setReconnectionInterval(interval);
    }

    String checkStatusIntervalValue = args.get(ARG_CHECK_STATUS_INTERVAL);
    if (checkStatusIntervalValue != null) {
      int checkStatusInterval = Integer.parseInt(checkStatusIntervalValue);
      request.setCheckStatusInterval(checkStatusInterval);
    }

    String heartbeatIntervalValue = args.get(ARG_HEARTBEAT_INTERVAL);
    if (heartbeatIntervalValue != null) {
      int heartbeatInterval = Integer.parseInt(heartbeatIntervalValue);
      request.setHeartbeatInterval(heartbeatInterval);
    }
  }

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
      case SITUATION_EXCHANGE:
        return new SituationExchangeRequestStructure();
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
      case SITUATION_EXCHANGE:
        return new SituationExchangeSubscriptionStructure();
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
      case SITUATION_EXCHANGE:
        applyArgsToSituationExchangeRequest(
            (SituationExchangeRequestStructure) moduleServiceRequest, args);
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
      case SITUATION_EXCHANGE:
        handleSituationExchangeSubscriptionSpecificArguments(
            (SituationExchangeSubscriptionStructure) moduleSubscription, args);
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

  private void handleSituationExchangeSubscriptionSpecificArguments(
      SituationExchangeSubscriptionStructure moduleSubscription,
      Map<String, String> args) {

    SituationExchangeRequestStructure request = new SituationExchangeRequestStructure();
    moduleSubscription.setSituationExchangeRequest(request);

    applyArgsToSituationExchangeRequest(request, args);
  }

  private void applyArgsToSituationExchangeRequest(
      SituationExchangeRequestStructure request, Map<String, String> args) {

  }

  private static DatatypeFactory createDataTypeFactory() {
    try {
      return DatatypeFactory.newInstance();
    } catch (DatatypeConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Date getIso8601StringAsTime(String value, TimeZone timeZone)
      throws ParseException {

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    format.setTimeZone(timeZone);

    int n = value.length();

    if (n > 6) {
      char c1 = value.charAt(n - 6);
      char c2 = value.charAt(n - 3);
      if ((c1 == '-' || c1 == '+') && c2 == ':')
        value = value.substring(0, n - 3) + value.substring(n - 2);
    }

    return format.parse(value);
  }
}
