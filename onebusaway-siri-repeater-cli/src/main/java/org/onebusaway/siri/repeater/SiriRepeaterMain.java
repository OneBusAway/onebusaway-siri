package org.onebusaway.siri.repeater;

import org.onebusaway.siri.core.guice.LifecycleService;

import com.google.inject.Injector;


public class SiriRepeaterMain {

  public static void main(String[] args) throws Exception {

    try {

      SiriRepeaterCommandLineConfiguration config = new SiriRepeaterCommandLineConfiguration();
      Injector injector = config.configure(args);

      LifecycleService service = injector.getInstance(LifecycleService.class);
      service.start();

    } catch (Exception ex) {
      System.err.println("error running the SIRI repeater application");
      ex.printStackTrace();
      System.exit(-1);
    }
  }
}
