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
package org.onebusaway.siri.core.guice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class JSR250ModuleTest {

  @Test
  public void test() {

    Injector injector = Guice.createInjector(new ServiceModule(),
        new JSR250Module());

    ServiceA serviceA = injector.getInstance(ServiceA.class);
    ServiceB serviceB = injector.getInstance(ServiceB.class);

    LifecycleService lifecycle = injector.getInstance(LifecycleService.class);

    lifecycle.start();

    assertTrue(serviceA.getConstructionCount() < serviceB.getConstructionCount());
    assertTrue(serviceB.getConstructionCount() < serviceA.getStartCount());
    assertTrue(serviceA.getStartCount() < serviceB.getStartCount());

    /**
     * @PreDestory hooks should not have been run yet
     */
    assertEquals(0, serviceB.getStopCount());
    assertEquals(0, serviceB.getStopCount());

    lifecycle.stop();

    /**
     * @PreDestroy should be applied in reverse order of @PostConstruct
     */
    assertTrue(serviceB.getStartCount() < serviceB.getStopCount());
    assertTrue(serviceB.getStopCount() < serviceA.getStopCount());
  }

  @Test
  public void testReverseInstanceOrder() {

    Injector injector = Guice.createInjector(new ServiceModule(),
        new JSR250Module());

    /**
     * We switch the order in which we request service A and B, just to make
     * sure that A is still instantiated and run first, even if we request B
     * first (since B depends on A)
     */
    ServiceB serviceB = injector.getInstance(ServiceB.class);
    ServiceA serviceA = injector.getInstance(ServiceA.class);
    
    LifecycleService lifecycle = injector.getInstance(LifecycleService.class);

    lifecycle.start();

    assertTrue(serviceA.getConstructionCount() < serviceB.getConstructionCount());
    assertTrue(serviceB.getConstructionCount() < serviceA.getStartCount());
    assertTrue(serviceA.getStartCount() < serviceB.getStartCount());

    /**
     * @PreDestory hooks should not have been run yet
     */
    assertEquals(0, serviceB.getStopCount());
    assertEquals(0, serviceB.getStopCount());

    lifecycle.stop();

    /**
     * @PreDestroy should be applied in reverse order of @PostConstruct
     */
    assertTrue(serviceB.getStartCount() < serviceB.getStopCount());
    assertTrue(serviceB.getStopCount() < serviceA.getStopCount());
  }

  @Singleton
  public static class ServiceA {

    private final AtomicInteger _counter;
    private int _constructionCount;
    private int _startCount;
    private int _stopCount;

    @Inject
    public ServiceA(AtomicInteger counter) {
      _counter = counter;
      _constructionCount = _counter.incrementAndGet();
    }

    public int getConstructionCount() {
      return _constructionCount;
    }

    public int getStartCount() {
      return _startCount;
    }

    public int getStopCount() {
      return _stopCount;
    }

    @PostConstruct
    public void start() {
      _startCount = _counter.incrementAndGet();
    }

    @PreDestroy
    public void stop() {
      _stopCount = _counter.incrementAndGet();
    }
  }

  @Singleton
  public static class ServiceB {

    private final AtomicInteger _counter;
    private int _constructionCount;
    private int _startCount;
    private int _stopCount;

    @Inject
    public ServiceB(AtomicInteger counter, ServiceA serviceA) {
      _counter = counter;
      _constructionCount = _counter.incrementAndGet();
    }

    public int getConstructionCount() {
      return _constructionCount;
    }

    public int getStartCount() {
      return _startCount;
    }

    public int getStopCount() {
      return _stopCount;
    }

    @PostConstruct
    public void start() {
      _startCount = _counter.incrementAndGet();
    }

    @PreDestroy
    public void stop() {
      _stopCount = _counter.incrementAndGet();
    }
  }

  private static class ServiceModule extends AbstractModule {

    @Override
    protected void configure() {
      bind(ServiceA.class);
      bind(ServiceB.class);
      // We want a singleton here, so we bind a specific instance
      bind(AtomicInteger.class).toInstance(new AtomicInteger());
    }
  }
}
