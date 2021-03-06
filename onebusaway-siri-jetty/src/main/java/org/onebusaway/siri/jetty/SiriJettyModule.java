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
package org.onebusaway.siri.jetty;

import java.net.URL;
import java.util.List;
import java.util.Set;

import javax.servlet.Servlet;

import org.onebusaway.guice.jetty_exporter.JettyExporterModule;
import org.onebusaway.guice.jetty_exporter.ServletSource;
import org.onebusaway.siri.core.SiriCommon;
import org.onebusaway.status_exporter.StatusJettyExporterModule;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

public class SiriJettyModule extends AbstractModule {

  public static void addModuleAndDependencies(Set<Module> modules) {
    JettyExporterModule module = JettyExporterModule.addModuleAndDependencies(modules);
    modules.add(new SiriJettyModule(module.getSources()));
    StatusJettyExporterModule.addModuleAndDependencies(modules);
  }

  final List<ServletSource> _sources;

  public SiriJettyModule(List<ServletSource> sources) {
    _sources = sources;
  }

  @Override
  protected void configure() {

    /**
     * The underlying {@link JettyExporterModule} will listen for
     * {@link ServletSource} instances, but we also want to listen for
     * {@link SiriCommon} instances which we wrap with a servlet source wrapper
     * of our own and add to the source so they'll be properly exported.
     */
    bindListener(Matchers.any(), new TypeListener() {
      @Override
      public <I> void hear(TypeLiteral<I> injectableType,
          TypeEncounter<I> encounter) {

        Class<? super I> type = injectableType.getRawType();

        if (SiriCommon.class.isAssignableFrom(type)) {
          encounter.register(new InjectionListenerImpl<I>(_sources));
        }
      }
    });
  }

  /**
   * Implement hashCode() and equals() such that two instances of the module
   * will be equal.
   */
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

    private final List<ServletSource> _sources;

    public InjectionListenerImpl(List<ServletSource> sources) {
      _sources = sources;
    }

    @Override
    public void afterInjection(I injectee) {
      SiriCommon common = (SiriCommon) injectee;
      SubscriptionServerServlet servlet = new SubscriptionServerServlet();
      servlet.setSiriListener(common);
      ServletSource source = new SiriCommonServletSource(common, servlet);
      _sources.add(source);
    }
  }

  private static class SiriCommonServletSource implements ServletSource {

    private SiriCommon _common;

    private Servlet _servlet;

    public SiriCommonServletSource(SiriCommon common, Servlet servlet) {
      _common = common;
      _servlet = servlet;
    }

    @Override
    public URL getUrl() {
      return _common.getInternalUrlToBind(false);
    }

    @Override
    public Servlet getServlet() {
      return _servlet;
    }
  }
}
