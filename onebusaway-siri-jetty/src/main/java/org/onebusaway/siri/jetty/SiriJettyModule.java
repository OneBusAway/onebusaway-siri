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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Servlet;

import org.onebusaway.siri.core.SiriCommon;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

public class SiriJettyModule extends AbstractModule {

  @Override
  protected void configure() {

    bind(StatusServletSource.class);

    try {
      bind(URL.class).annotatedWith(Names.named(StatusServletSource.URL_NAME)).toInstance(
          new URL("http://localhost/status"));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }

    bind(Servlet.class).annotatedWith(
        Names.named(StatusServletSource.SERVLET_NAME)).to(StatusServlet.class);

    final List<ServletSource> sources = new ArrayList<ServletSource>();

    bindListener(Matchers.any(), new TypeListener() {
      @Override
      public <I> void hear(TypeLiteral<I> injectableType,
          TypeEncounter<I> encounter) {

        Class<? super I> type = injectableType.getRawType();

        if (SiriCommon.class.isAssignableFrom(type)
            || ServletSource.class.isAssignableFrom(type)) {
          encounter.register(new InjectionListenerImpl<I>(sources));
        }
      }
    });

    bind(SiriJettyServiceImpl.class).toInstance(
        new SiriJettyServiceImpl(sources));
  }

  private static class InjectionListenerImpl<I> implements InjectionListener<I> {

    private final List<ServletSource> _sources;

    public InjectionListenerImpl(List<ServletSource> sources) {
      _sources = sources;
    }

    @Override
    public void afterInjection(I injectee) {
      if (injectee instanceof SiriCommon) {
        SiriCommon common = (SiriCommon) injectee;
        SubscriptionServerServlet servlet = new SubscriptionServerServlet();
        servlet.setSiriListener(common);
        ServletSource source = new SiriCommonServletSource(common, servlet);
        _sources.add(source);
      } else if (injectee instanceof ServletSource) {
        _sources.add((ServletSource) injectee);
      }
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
