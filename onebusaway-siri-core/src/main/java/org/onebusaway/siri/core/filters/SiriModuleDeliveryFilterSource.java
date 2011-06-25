package org.onebusaway.siri.core.filters;

public class SiriModuleDeliveryFilterSource {

  private SiriModuleDeliveryFilterMatcher matcher;

  private SiriModuleDeliveryFilter filter;

  public SiriModuleDeliveryFilterMatcher getMatcher() {
    return matcher;
  }

  public void setMatcher(SiriModuleDeliveryFilterMatcher matcher) {
    this.matcher = matcher;
  }

  public SiriModuleDeliveryFilter getFilter() {
    return filter;
  }

  public void setFilter(SiriModuleDeliveryFilter filter) {
    this.filter = filter;
  }
}
