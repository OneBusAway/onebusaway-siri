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
package org.onebusaway.siri.core.services;

/**
 * Computes an exponential weighted average where new entries are weighted based
 * on how long it has been since the previous entry (shorter time => less weight
 * 
 * @author bdferris
 * 
 */
public class ExponentialWeightedAverageForTimeWindow {

  private final long window;

  private double average;

  private long prevTime;

  public ExponentialWeightedAverageForTimeWindow(long window) {
    this.window = window;
  }

  public void addValueAtTime(double value, long time) {
    if (prevTime == 0) {
      average = value;
    } else {
      double delta = time - prevTime;
      double alpha = 1 - Math.exp(-1 * delta / window);
      average = alpha * value + (1 - alpha) * average;
    }
    prevTime = time;
  }

  public double getAverage() {
    return average;
  }
}
