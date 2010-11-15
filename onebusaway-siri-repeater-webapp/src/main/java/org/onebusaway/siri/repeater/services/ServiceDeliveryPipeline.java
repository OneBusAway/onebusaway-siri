package org.onebusaway.siri.repeater.services;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.siri.repeater.model.SubscriptionTarget;

public class ServiceDeliveryPipeline {

  private List<ServiceDeliveryTransformation> _transformations = new ArrayList<ServiceDeliveryTransformation>();

  private List<SubscriptionTarget> _targets = new ArrayList<SubscriptionTarget>();

  public List<ServiceDeliveryTransformation> getTransformations() {
    return _transformations;
  }

  public synchronized List<SubscriptionTarget> getTargets() {
    return _targets;
  }

  public synchronized void addTarget(SubscriptionTarget target) {
    _targets = new ArrayList<SubscriptionTarget>(_targets);
    _targets.add(target);
  }
}
