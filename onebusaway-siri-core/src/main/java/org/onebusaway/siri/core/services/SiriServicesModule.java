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
package org.onebusaway.siri.core.services;

import java.util.HashSet;
import java.util.Set;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

/**
 * Guice module for wiring a number of base SIRI services.
 * 
 * @author bdferris
 * 
 */
public class SiriServicesModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SchedulingService.class).to(SchedulingServiceImpl.class);
    bind(HttpClientService.class).to(HttpClientServiceImpl.class);

    final Set<StatusProviderService> providers = new HashSet<StatusProviderService>();
    bind(new TypeLiteral<Set<StatusProviderService>>() {
    }).annotatedWith(Names.named(StatusService.PROVIDERS_NAME)).toInstance(
        providers);

    bind(StatusService.class);

    /**
     * Collect all the StatusProviderService instances as they are instantiated.
     */
    bindListener(Matchers.any(), new TypeListener() {
      @Override
      public <I> void hear(TypeLiteral<I> injectableType,
          TypeEncounter<I> encounter) {

        Class<? super I> type = injectableType.getRawType();

        if (StatusProviderService.class.isAssignableFrom(type)) {
          encounter.register(new InjectionListenerImpl<I>(providers));
        }
      }
    });
  }

  @Override
  public int hashCode() {
    return this.getClass().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null)
      return false;
    return this.getClass().equals(o.getClass());
  }

  private static class InjectionListenerImpl<I> implements InjectionListener<I> {

    private final Set<StatusProviderService> _providers;

    public InjectionListenerImpl(Set<StatusProviderService> providers) {
      _providers = providers;
    }

    @Override
    public void afterInjection(I injectee) {
      if (injectee instanceof StatusProviderService) {
        _providers.add((StatusProviderService) injectee);
      }
    }
  }
}
