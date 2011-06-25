package org.onebusaway.siri.core.filters;

import java.util.ArrayList;
import java.util.List;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;

public class ModuleDeliveryFilterCollection implements SiriModuleDeliveryFilter {

  private List<SiriModuleDeliveryFilter> _filters = new ArrayList<SiriModuleDeliveryFilter>();

  public void addFilter(SiriModuleDeliveryFilter filter) {
    _filters.add(filter);
  }

  @Override
  public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
      AbstractServiceDeliveryStructure moduleDelivery) {

    for (SiriModuleDeliveryFilter filter : _filters) {
      moduleDelivery = filter.filter(delivery, moduleDelivery);
      if (moduleDelivery == null)
        break;
    }

    return moduleDelivery;
  }
}
