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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import uk.org.siri.siri.BlockRefStructure;
import uk.org.siri.siri.DataFrameRefStructure;
import uk.org.siri.siri.ErrorDescriptionStructure;
import uk.org.siri.siri.JourneyPatternRefStructure;
import uk.org.siri.siri.LineRefStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.NaturalLanguageStringStructure;
import uk.org.siri.siri.OperatorRefStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.VehicleRefStructure;

public class SiriTypeFactory {

  private static SimpleDateFormat _dataFrameRefFormat = new SimpleDateFormat(
      "yyyy-MM-dd");

  private static final DatatypeFactory _dataTypeFactory = createDataTypeFactory();

  public static ParticipantRefStructure particpantRef(String value) {
    ParticipantRefStructure ref = new ParticipantRefStructure();
    ref.setValue(value);
    return ref;
  }

  public static MessageQualifierStructure messageId(String value) {
    MessageQualifierStructure messageId = new MessageQualifierStructure();
    messageId.setValue(value);
    return messageId;
  }

  public static MessageQualifierStructure randomMessageId() {
    return messageId(UUID.randomUUID().toString());
  }

  public static SubscriptionQualifierStructure subscriptionId(
      String subscriptionId) {
    SubscriptionQualifierStructure s = new SubscriptionQualifierStructure();
    s.setValue(subscriptionId);
    return s;
  }

  public static SubscriptionQualifierStructure randomSubscriptionId() {
    return subscriptionId(UUID.randomUUID().toString());
  }

  public static ErrorDescriptionStructure errorDescription(String value) {
    ErrorDescriptionStructure desc = new ErrorDescriptionStructure();
    desc.setValue(value);
    return desc;
  }

  public static NaturalLanguageStringStructure nls(String value) {
    NaturalLanguageStringStructure str = new NaturalLanguageStringStructure();
    str.setValue(value);
    return str;
  }

  public static Duration duration(long durationInMilliSeconds) {
    return _dataTypeFactory.newDuration(durationInMilliSeconds);
  }

  public static DataFrameRefStructure dataFrameRef(String value) {
    DataFrameRefStructure ref = new DataFrameRefStructure();
    ref.setValue(value);
    return ref;
  }

  public static DataFrameRefStructure dataFrameRef(Date value) {
    return dataFrameRef(_dataFrameRefFormat.format(value));
  }

  public static LineRefStructure lineRef(String value) {
    LineRefStructure ref = new LineRefStructure();
    ref.setValue(value);
    return ref;
  }

  public static JourneyPatternRefStructure journeyPatternRef(String value) {
    JourneyPatternRefStructure ref = new JourneyPatternRefStructure();
    ref.setValue(value);
    return ref;
  }

  public static OperatorRefStructure operatorRef(String value) {
    OperatorRefStructure ref = new OperatorRefStructure();
    ref.setValue(value);
    return ref;
  }

  public static BlockRefStructure blockRef(String value) {
    BlockRefStructure ref = new BlockRefStructure();
    ref.setValue(value);
    return ref;
  }

  private static DatatypeFactory createDataTypeFactory() {
    try {
      return DatatypeFactory.newInstance();
    } catch (DatatypeConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  public static VehicleRefStructure vehicleRef(String value) {
    VehicleRefStructure ref = new VehicleRefStructure();
    ref.setValue(value);
    return ref;
  }
}
