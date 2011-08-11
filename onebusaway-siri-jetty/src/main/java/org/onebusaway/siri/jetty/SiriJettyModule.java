package org.onebusaway.siri.jetty;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.siri.core.SiriCommon;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

public class SiriJettyModule extends AbstractModule {

  @Override
  protected void configure() {

    final List<SiriCommon> services = new ArrayList<SiriCommon>();

    bindListener(Matchers.any(), new TypeListener() {
      @Override
      public <I> void hear(TypeLiteral<I> injectableType,
          TypeEncounter<I> encounter) {

        Class<? super I> type = injectableType.getRawType();

        if (SiriCommon.class.isAssignableFrom(type))
          encounter.register(new InjectionListenerImpl<I>(services));
      }
    });

    bind(SiriJettyServiceImpl.class).toInstance(
        new SiriJettyServiceImpl(services));
  }

  private static class InjectionListenerImpl<I> implements InjectionListener<I> {

    private final List<SiriCommon> _services;

    public InjectionListenerImpl(List<SiriCommon> services) {
      _services = services;
    }

    @Override
    public void afterInjection(I injectee) {
      _services.add((SiriCommon) injectee);
    }
  }
}
