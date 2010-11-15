package org.onebusaway.siri.repeater.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.onebusaway.siri.repeater.model.SubscriptionTarget;
import org.onebusaway.siri.repeater.services.ServiceDeliveryTransformation;
import org.onebusaway.siri.repeater.services.SiriSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;

public class ServiceDeliveryToSubscriptionTargetTask implements Runnable {

  private static Logger _log = LoggerFactory.getLogger(ServiceDeliveryToSubscriptionTargetTask.class);

  private SiriSerializationService _siriSerializationService;

  private ServiceDelivery _delivery;

  private SubscriptionTarget _target;

  @Autowired
  public void setSiriSerializationService(
      SiriSerializationService siriSerializationService) {
    _siriSerializationService = siriSerializationService;
  }

  public void setServiceDelivery(ServiceDelivery delivery) {
    _delivery = delivery;
  }

  public void setTarget(SubscriptionTarget target) {
    _target = target;
  }

  @Override
  public void run() {

    /**
     * Filter the service _delivery as appropriate for our subscription target
     */
    ServiceDeliveryTransformation filter = _target.getFilter();
    if (filter != null) {
      _delivery = filter.transform(_delivery);
      if (_delivery == null)
        return;
    }

    updateSubscriptionReferences();

    String contents = getDeliveryAsString();

    try {
      sendDeliveryToClient(contents);
    } catch (Exception ex) {
      _log.warn("error sending service delivery to client", ex);
    }
  }

  /**
   * Apply the subscription target information to each relevant service delivery
   * structure
   */
  private void updateSubscriptionReferences() {
    applySubscriberRef(_delivery.getConnectionMonitoringDistributorDelivery(),
        _target);
    applySubscriberRef(_delivery.getConnectionMonitoringFeederDelivery(),
        _target);
    applySubscriberRef(_delivery.getEstimatedTimetableDelivery(), _target);
    applySubscriberRef(_delivery.getFacilityMonitoringDelivery(), _target);
    applySubscriberRef(_delivery.getGeneralMessageDelivery(), _target);
    applySubscriberRef(_delivery.getProductionTimetableDelivery(), _target);
    applySubscriberRef(_delivery.getSituationExchangeDelivery(), _target);
    applySubscriberRef(_delivery.getStopMonitoringDelivery(), _target);
    applySubscriberRef(_delivery.getStopTimetableDelivery(), _target);
    applySubscriberRef(_delivery.getVehicleMonitoringDelivery(), _target);
  }

  private void sendDeliveryToClient(String contents)
      throws UnsupportedEncodingException, IOException, ClientProtocolException {

    DefaultHttpClient client = new DefaultHttpClient();

    HttpPost post = new HttpPost(_target.getConsumerAddress());
    post.setEntity(new StringEntity(contents));

    HttpResponse response = client.execute(post);
    StatusLine statusLine = response.getStatusLine();

    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      _log.warn("error sending service _delivery to subscription target: subscriberRef="
          + _target.getSubscriberRef().getValue()
          + " consumerAddress="
          + _target.getConsumerAddress()
          + " statusCode="
          + statusLine.getStatusCode());
    }
  }

  private String getDeliveryAsString() {
    StringWriter writer = new StringWriter();
    _siriSerializationService.marshall(_delivery, writer);
    String contents = writer.toString();
    return contents;
  }

  private <T extends AbstractServiceDeliveryStructure> void applySubscriberRef(
      List<T> deliveries, SubscriptionTarget target) {
    for (AbstractServiceDeliveryStructure delivery : deliveries) {
      delivery.setSubscriberRef(target.getSubscriberRef());
      delivery.setSubscriptionRef(target.getSubscriptionRef());
      delivery.setValidUntil(target.getValidUntil());
    }
  }
}