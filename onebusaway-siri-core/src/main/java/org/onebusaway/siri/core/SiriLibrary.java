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
package org.onebusaway.siri.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ObjectUtils;
import org.onebusaway.collections.PropertyPathExpression;
import org.onebusaway.siri.core.versioning.IntrospectionVersionConverter;
import org.onebusaway.siri.core.versioning.PackageBasedTypeMappingStrategy;
import org.onebusaway.siri.core.versioning.SiriVersioning;
import org.onebusaway.siri.core.versioning.TypeMappingStrategy;
import org.onebusaway.siri.core.versioning.VersionConverter;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AbstractServiceRequestStructure;
import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SubscriptionRequest;

/**
 * SIRI utility functions
 * 
 * @author bdferris
 */
public class SiriLibrary {

  /**
   * This is used to make deep copies of SIRI structures
   */
  private static VersionConverter _copier;

  static {
    TypeMappingStrategy selfMapping = new PackageBasedTypeMappingStrategy(
        SiriVersioning.SIRI_1_3_PACKAGE, SiriVersioning.SIRI_1_3_PACKAGE);
    _copier = new IntrospectionVersionConverter(selfMapping);
  }

  @SuppressWarnings("unchecked")
  public static <T extends AbstractServiceRequestStructure> List<T> getServiceRequestsForModule(
      ServiceRequest serviceRequest, ESiriModuleType moduleType) {

    switch (moduleType) {
      case VEHICLE_MONITORING:
        return (List<T>) serviceRequest.getVehicleMonitoringRequest();
      case SITUATION_EXCHANGE:
        return (List<T>) serviceRequest.getSituationExchangeRequest();
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
      case SITUATION_EXCHANGE:
        return (List<T>) subscriptionRequest.getSituationExchangeSubscriptionRequest();
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
      case SITUATION_EXCHANGE:
        return (List<T>) serviceDelivery.getSituationExchangeDelivery();
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
  
  public static Siri copy(Siri payload) {
    return (Siri) _copier.convert(payload);
  }

  public static AbstractServiceDeliveryStructure deepCopyModuleDelivery(
      ESiriModuleType moduleType, AbstractServiceDeliveryStructure from) {
    return (AbstractServiceDeliveryStructure) _copier.convert(from);
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

  public static Map<String, String> getLineAsMap(String line) {
    String[] tokens = line.split(",");
    Map<String, String> subArgs = new HashMap<String, String>();
    for (String token : tokens) {
      int index = token.indexOf('=');
      if (index != -1) {
        String key = token.substring(0, index);
        String value = token.substring(index + 1);
        subArgs.put(key, value);
      } else {
        subArgs.put(token, null);
      }
    }
    return subArgs;
  }
  
  public static boolean needsHelp(String[] args) {
    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help") || arg.equals("-help"))
        return true;
    }
    return false;
  }
}
