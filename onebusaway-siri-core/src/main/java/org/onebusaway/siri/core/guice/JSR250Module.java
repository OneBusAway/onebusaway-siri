package org.onebusaway.siri.core.guice;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

/**
 * Adds support to Guice for JSR520 annotations, including {@link PostConstruct}
 * and {@link PreDestroy}.
 * 
 * @author bdferris
 */
public class JSR250Module extends AbstractModule {

  private static final Logger _log = LoggerFactory.getLogger(JSR250Module.class);

  @Override
  protected void configure() {

    final List<ObjectAndMethod> postConstructActions = new ArrayList<ObjectAndMethod>();
    final List<ObjectAndMethod> preDestroyActions = new ArrayList<ObjectAndMethod>();

    bindListener(Matchers.any(), new TypeListener() {

      @Override
      public <I> void hear(TypeLiteral<I> injectableType,
          TypeEncounter<I> encounter) {

        Class<? super I> type = injectableType.getRawType();
        Method[] methods = type.getDeclaredMethods();

        for (final Method method : methods) {

          PostConstruct postConstruct = method.getAnnotation(PostConstruct.class);
          if (postConstruct != null)
            encounter.register(new RegisterMethodCallback<I>(
                postConstructActions, method));

          PreDestroy preDestory = method.getAnnotation(PreDestroy.class);
          if (preDestory != null)
            encounter.register(new RegisterMethodCallback<I>(preDestroyActions,
                method));
        }
      }
    });

    LifecycleServiceImpl service = new LifecycleServiceImpl(
        postConstructActions, preDestroyActions);
    bind(LifecycleService.class).toInstance(service);

    Runtime runtime = Runtime.getRuntime();
    PreDestroyShutdownHook hook = new PreDestroyShutdownHook(service);
    runtime.addShutdownHook(new Thread(hook));
  }

  private static class ObjectAndMethod {
    private final Object object;

    private final Method method;

    private boolean hasBeenRun = false;

    public ObjectAndMethod(Object object, Method method) {
      this.object = object;
      this.method = method;
    }

    public void execute() {

      synchronized (this) {
        if (hasBeenRun)
          return;
        hasBeenRun = true;
      }

      try {
        method.setAccessible(true);
        method.invoke(object);
      } catch (Throwable ex) {
        _log.warn("error invoking @PreDestroy method " + method + " on target "
            + object, ex);
      }
    }
  }

  private static class RegisterMethodCallback<I> implements
      InjectionListener<I> {

    private final List<ObjectAndMethod> _actions;

    private final Method _method;

    public RegisterMethodCallback(List<ObjectAndMethod> preDestroyActions,
        Method method) {
      _actions = preDestroyActions;
      _method = method;
    }

    @Override
    public void afterInjection(I injectee) {
      _actions.add(new ObjectAndMethod(injectee, _method));
    }
  }

  private static class LifecycleServiceImpl implements LifecycleService {

    private final List<ObjectAndMethod> _postConstructActions;
    private final List<ObjectAndMethod> _preDestroyActions;

    public LifecycleServiceImpl(List<ObjectAndMethod> postConstructActions,
        List<ObjectAndMethod> preDestroyActions) {
      _postConstructActions = postConstructActions;
      _preDestroyActions = preDestroyActions;
    }

    @Override
    public void start() {
      for (ObjectAndMethod target : _postConstructActions)
        target.execute();
    }

    @Override
    public void stop() {
      /**
       * The @PreDestroy actions need to be applied in reverse order of bean
       * instantiation
       */
      for (int i = _preDestroyActions.size() - 1; i >= 0; --i) {
        ObjectAndMethod target = _preDestroyActions.get(i);
        target.execute();
      }
    }
  }

  private static class PreDestroyShutdownHook implements Runnable {

    private LifecycleService _service;

    public PreDestroyShutdownHook(LifecycleService service) {
      _service = service;
    }

    @Override
    public void run() {
      _service.stop();
    }
  }
}
