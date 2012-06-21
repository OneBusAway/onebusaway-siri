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
package org.onebusaway.siri;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriClientRequestFactory;
import org.onebusaway.siri.core.SiriCoreModule;
import org.onebusaway.siri.core.SiriServer;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.siri.core.services.SchedulingService;
import org.onebusaway.siri.core.subscriptions.client.SiriClientSubscriptionManager;
import org.onebusaway.siri.core.subscriptions.server.SiriServerSubscriptionManager;
import org.onebusaway.siri.jetty.SiriJettyModule;
import org.onebusaway.status_exporter.StatusProviderService;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class SiriClientAndServerIntegrationTest {

  @Test
  public void testSimplePubSub() throws InterruptedException {

    Injector clientInjector = createInstance();
    Injector serverInjector = createInstance();

    String serverUrl = configureUrls(clientInjector, serverInjector);

    SiriClient client = clientInjector.getInstance(SiriClient.class);
    SiriServer server = serverInjector.getInstance(SiriServer.class);

    DeliveryHandler handler = new DeliveryHandler();
    client.addServiceDeliveryHandler(handler);

    LifecycleService clientLifecycle = clientInjector.getInstance(LifecycleService.class);
    LifecycleService serverLifecycle = serverInjector.getInstance(LifecycleService.class);

    serverLifecycle.start();
    clientLifecycle.start();

    SiriClientRequest request = constructVehicleMonitoringRequest(serverUrl);
    client.handleRequest(request);

    Thread.sleep(1000);

    VehicleMonitoringDeliveryStructure vm = new VehicleMonitoringDeliveryStructure();
    vm.setDefaultLanguage("fr");
    VehicleActivityStructure va = new VehicleActivityStructure();
    vm.getVehicleActivity().add(va);
    ServiceDelivery delivery = new ServiceDelivery();
    delivery.getVehicleMonitoringDelivery().add(vm);

    server.publish(delivery);

    Thread.sleep(1000);

    assertEquals(1, handler.getDeliveryCount());
    delivery = handler.getDelivery();
    assertEquals(1, delivery.getVehicleMonitoringDelivery().size());
    vm = delivery.getVehicleMonitoringDelivery().get(0);
    assertEquals("fr", vm.getDefaultLanguage());

    clientLifecycle.stop();
    serverLifecycle.stop();
  }

  @Test
  public void testDisconnectAndReconnect() throws InterruptedException {

    Injector clientInjector = createInstance();
    Injector serverInjector = createInstance();

    String serverUrl = configureUrls(clientInjector, serverInjector);

    SiriClient client = clientInjector.getInstance(SiriClient.class);

    LifecycleService clientLifecycle = clientInjector.getInstance(LifecycleService.class);
    LifecycleService serverLifecycle = serverInjector.getInstance(LifecycleService.class);

    serverLifecycle.start();
    clientLifecycle.start();

    SiriClientRequest request = constructVehicleMonitoringRequest(serverUrl);
    client.handleRequest(request);

    Thread.sleep(1000);

    serverLifecycle.stop();

    Thread.sleep(1000);

    serverLifecycle.start();

    Thread.sleep(10000);

    SiriClientSubscriptionManager clientManager = clientInjector.getInstance(SiriClientSubscriptionManager.class);
    Map<String, String> clientStatus = getStatus(clientManager);
    assertEquals("1", clientStatus.get("siri.client.activeSubscriptions"));

    SiriServerSubscriptionManager serverManager = serverInjector.getInstance(SiriServerSubscriptionManager.class);
    Map<String, String> servertStatus = getStatus(serverManager);
    assertEquals("1", servertStatus.get("siri.server.activeSubscriptions"));

    clientLifecycle.stop();
    serverLifecycle.stop();
  }

  @Test
  public void testDisconnectAndEventualReconnect() throws InterruptedException {

    Injector clientInjector = createInstance();
    Injector serverInjector = createInstance();

    String serverUrl = configureUrls(clientInjector, serverInjector);

    SiriClient client = clientInjector.getInstance(SiriClient.class);

    LifecycleService clientLifecycle = clientInjector.getInstance(LifecycleService.class);
    LifecycleService serverLifecycle = serverInjector.getInstance(LifecycleService.class);

    serverLifecycle.start();
    clientLifecycle.start();

    SiriClientRequest request = constructVehicleMonitoringRequest(serverUrl);
    client.handleRequest(request);

    Thread.sleep(1000);

    serverLifecycle.stop();

    /**
     * Sleep until we're sure we've hit a check interval request
     */
    Thread.sleep(10000);

    serverLifecycle.start();

    /**
     * Sleep until we're sure a reconnection has been established
     */
    Thread.sleep(10000);

    SiriClientSubscriptionManager clientManager = clientInjector.getInstance(SiriClientSubscriptionManager.class);
    Map<String, String> clientStatus = getStatus(clientManager);
    assertEquals("1", clientStatus.get("siri.client.activeSubscriptions"));

    SiriServerSubscriptionManager serverManager = serverInjector.getInstance(SiriServerSubscriptionManager.class);
    Map<String, String> servertStatus = getStatus(serverManager);
    assertEquals("1", servertStatus.get("siri.server.activeSubscriptions"));

    clientLifecycle.stop();
    serverLifecycle.stop();
  }

  private Injector createInstance() {
    Set<Module> modules = new HashSet<Module>();
    SiriCoreModule.addModuleAndDependencies(modules);
    SiriJettyModule.addModuleAndDependencies(modules);
    Injector injector = Guice.createInjector(modules);

    /**
     * Set a short timeout so that we'll detect errors quickly.
     */
    injector.getInstance(SchedulingService.class).setResponseTimeout(1);
    return injector;
  }

  private String configureUrls(Injector clientInjector, Injector serverInjector) {

    int clientPort = Integer.parseInt(System.getProperty(
        "org_onebusaway_test_port", "8080"));
    int serverPort = clientPort + 1;

    SiriClient client = clientInjector.getInstance(SiriClient.class);
    SiriServer server = serverInjector.getInstance(SiriServer.class);

    String clientUrl = "http://localhost:" + clientPort + "/client.xml";
    client.setUrl(clientUrl);
    String serverUrl = "http://localhost:" + serverPort + "/server.xml";
    server.setUrl(serverUrl);

    return serverUrl;
  }

  private SiriClientRequest constructVehicleMonitoringRequest(String serverUrl) {
    SiriClientRequestFactory factory = new SiriClientRequestFactory();
    Map<String, String> args = new HashMap<String, String>();
    args.put(SiriClientRequestFactory.ARG_URL, serverUrl);
    args.put(SiriClientRequestFactory.ARG_MODULE_TYPE, "VEHICLE_MONITORING");
    args.put(SiriClientRequestFactory.ARG_CHECK_STATUS_INTERVAL, "5");
    args.put(SiriClientRequestFactory.ARG_RECONNECTION_ATTEMPTS, "-1");
    args.put(SiriClientRequestFactory.ARG_RECONNECTION_INTERVAL, "5");

    SiriClientRequest request = factory.createSubscriptionRequest(args);
    return request;
  }

  private Map<String, String> getStatus(StatusProviderService provider) {
    Map<String, String> status = new HashMap<String, String>();
    provider.getStatus(status);
    return status;
  }

  private class DeliveryHandler implements SiriServiceDeliveryHandler {

    private volatile ServiceDelivery _lastDelivery;

    private volatile int _deliveryCount;

    public ServiceDelivery getDelivery() {
      return _lastDelivery;
    }

    public int getDeliveryCount() {
      return _deliveryCount;
    }

    @Override
    public synchronized void handleServiceDelivery(SiriChannelInfo channelInfo,
        ServiceDelivery serviceDelivery) {
      _lastDelivery = serviceDelivery;
      _deliveryCount++;
    }

  }
}
