/**
 * Copyright (C) 2012 Google, Inc.
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
package org.onebusaway.siri.core.subscriptions.server;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.onebusaway.siri.core.SiriTypeFactory;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.onebusaway.siri.core.versioning.ESiriVersion;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.VehicleMonitoringSubscriptionStructure;

public class SiriServerSubscriptionManagerTest {

  private SiriServerSubscriptionManager _manager;

  @Before
  public void before() {
    _manager = new SiriServerSubscriptionManager();
  }

  @Test
  public void testRecordPublicationStatistics() {

    VehicleMonitoringSubscriptionStructure vmRequest = new VehicleMonitoringSubscriptionStructure();
    vmRequest.setSubscriberRef(SiriTypeFactory.particpantRef("alpha"));
    vmRequest.setSubscriptionIdentifier(SiriTypeFactory.subscriptionId("beta"));

    SubscriptionRequest request = new SubscriptionRequest();
    request.setAddress("10.0.0.1");
    request.getVehicleMonitoringSubscriptionRequest().add(vmRequest);

    List<StatusResponseStructure> statuses = new ArrayList<StatusResponseStructure>();
    _manager.handleSubscriptionRequest(request, ESiriVersion.V1_3, statuses);

    ServiceDelivery delivery = new ServiceDelivery();
    delivery.setResponseTimestamp(new Date(System.currentTimeMillis() - 2000));

    SiriServerSubscriptionEvent event = new SiriServerSubscriptionEvent(
        new SubscriptionId("alpha", "beta"), "10.0.0.1", ESiriVersion.V1_3,
        delivery);
    _manager.recordPublicationStatistics(event, 1000, true);

    Map<String, String> status = new HashMap<String, String>();
    _manager.getStatus(status);

    assertEquals("1000",
        status.get("siri.server.channel[10.0.0.1].averageTimeNeededToPublish"));
    long delay = Long.parseLong(status.get("siri.server.channel[10.0.0.1].averagePublicationDelay"));
    assertEquals(delay, 2000.0, 10.0);
    assertEquals("1",
        status.get("siri.server.channel[10.0.0.1].connectionErrorCount"));
  }

}
