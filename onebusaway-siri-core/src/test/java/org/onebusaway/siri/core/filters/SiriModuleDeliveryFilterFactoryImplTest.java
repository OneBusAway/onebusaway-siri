/**
 * Copyright (C) 2014 Google, Inc.
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
package org.onebusaway.siri.core.filters;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.onebusaway.siri.core.exceptions.SiriException;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;

public class SiriModuleDeliveryFilterFactoryImplTest {

  private SiriModuleDeliveryFilterFactoryImpl _factory = new SiriModuleDeliveryFilterFactoryImpl();

  @Test
  public void testFullClassName() {
    Map<String, String> filterArgs = new HashMap<String, String>();
    filterArgs.put("Filter.Class",
        "org.onebusaway.siri.core.filters.ModuleDeliveryFilterCollection");
    SiriModuleDeliveryFilter filter = _factory.create(filterArgs);
    assertTrue(filter instanceof ModuleDeliveryFilterCollection);
  }

  @Test
  public void testPartialClassName() {
    Map<String, String> filterArgs = new HashMap<String, String>();
    filterArgs.put("Filter.Class", "ModuleDeliveryFilterCollection");
    SiriModuleDeliveryFilter filter = _factory.create(filterArgs);
    assertTrue(filter instanceof ModuleDeliveryFilterCollection);
  }

  @Test(expected = SiriException.class)
  public void testClassDoesNotExist() {
    Map<String, String> filterArgs = new HashMap<String, String>();
    filterArgs.put("Filter.Class", "DoesNotExist");
    _factory.create(filterArgs);
  }
  
  @Test
  public void testSetFilterArgs() {
    Map<String, String> filterArgs = new HashMap<String, String>();
    filterArgs.put("Filter.Class", DummyFilter.class.getName());
    filterArgs.put("value", "tacos");
    DummyFilter filter = (DummyFilter) _factory.create(filterArgs);
    assertEquals("tacos", filter.getValue());    
  }
  
  public static class DummyFilter implements SiriModuleDeliveryFilter {
    
    private String value;

    public void setValue(String value) {
      this.value = value;
    }
    
    public String getValue() {
      return value;
    }

    @Override
    public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
        AbstractServiceDeliveryStructure moduleDelivery) {
      return moduleDelivery;
    }    
  }
}
