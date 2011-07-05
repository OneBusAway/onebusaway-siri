package org.onebusaway.siri.repeater;


public class SiriRepeaterMain {

  public static void main(String[] args) throws Exception {

    try {

      SiriRepeaterCommandLineConfiguration config = new SiriRepeaterCommandLineConfiguration();
      SiriRepeater repeater = config.configure(args);

      /**
       * Register a shutdown hook to clean up the repeater when things shutdown
       */
      Thread shutdownHook = new Thread(new ShutdownHookRunnable(repeater));
      Runtime.getRuntime().addShutdownHook(shutdownHook);

      repeater.start();

    } catch (Exception ex) {
      System.err.println("error running the SIRI repeater application");
      ex.printStackTrace();
      System.exit(-1);
    }
  }

  private static class ShutdownHookRunnable implements Runnable {

    private final SiriRepeater _repeater;

    public ShutdownHookRunnable(SiriRepeater repeater) {
      _repeater = repeater;
    }

    @Override
    public void run() {
      _repeater.stop();
    }
  }
}
