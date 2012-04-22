/**
 * Copyright (C) 2012 Google Inc.
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExponentialWeightedAverageForTimeWindowTest {

  @Test
  public void test() {

    ExponentialWeightedAverageForTimeWindow wa = new ExponentialWeightedAverageForTimeWindow(
        1000);

    long t = 0;

    double[] v1 = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    t = addValuesAndCheckAverages(wa, t, 250, 1.0, v1);

    double[] v2 = {
        1.22, 1.39, 1.53, 1.63, 1.71, 1.78, 1.83, 1.86, 1.89, 1.92, 1.94, 1.95};
    t = addValuesAndCheckAverages(wa, t, 250, 2.0, v2);

    double[] v3 = {
        3.73, 5.12, 6.20, 7.04, 7.69, 8.20, 8.60, 8.91, 9.15, 9.34, 9.49, 9.60};
    t = addValuesAndCheckAverages(wa, t, 250, 10, v3);

    double[] v4 = {
        7.25, 5.43, 4.01, 2.90, 2.04, 1.37, 0.84, 0.43, 0.12, -0.13, -0.32,
        -0.47};
    t = addValuesAndCheckAverages(wa, t, 250, -1, v4);
  }

  private long addValuesAndCheckAverages(
      ExponentialWeightedAverageForTimeWindow wa, long t, long timestep,
      double value, double[] values) {

    for (int i = 0; i < values.length; i++) {
      wa.addValueAtTime(value, t);
      assertEquals(values[i], wa.getAverage(), 0.01);
      t += timestep;
    }
    return t;
  }

}
