package org.onebusaway.siri.repeater.controllers;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.onebusaway.siri.repeater.model.exceptions.SiriRepeaterException;
import org.onebusaway.siri.repeater.model.exceptions.UnexpectedSiriObjectException;
import org.onebusaway.siri.repeater.services.SiriRepeaterService;
import org.onebusaway.siri.repeater.services.SiriSerializationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SubscriptionRequest;

@Controller
public class SiriController {

  private SiriSerializationService _siriSerializationService;
  private SiriRepeaterService _siriRepeaterService;

  @Autowired
  public void setSiriSerializationService(
      SiriSerializationService siriSerializationService) {
    _siriSerializationService = siriSerializationService;
  }

  @Autowired
  public void setSiriRepeaterService(SiriRepeaterService siriRepeaterService) {
    _siriRepeaterService = siriRepeaterService;
  }

  @RequestMapping(value = "/siri-consumer", method = {
      RequestMethod.GET, RequestMethod.POST})
  public ModelAndView consumer(@RequestBody String body)
      throws UnexpectedSiriObjectException {

    Object obj = _siriSerializationService.unmarshal(body);

    if (obj instanceof ServiceDelivery) {
      _siriRepeaterService.handleServiceDelivery((ServiceDelivery) obj);
      return new ModelAndView("index.jspx");
    }

    throw new UnexpectedSiriObjectException(obj);
  }

  @RequestMapping(value = "/siri-producer", method = {
      RequestMethod.GET, RequestMethod.POST})
  public ModelAndView producer(@RequestBody String body,
      HttpServletResponse response) throws IOException, SiriRepeaterException {

    Object object = _siriSerializationService.unmarshal(body);

    if (object instanceof ServiceRequest) {

      ServiceRequest request = (ServiceRequest) object;

      if (hasAddress(request)) {
        /**
         * If a return address has been specified, we let the repeater perform
         * an asynchronous response
         */
        _siriRepeaterService.handlServiceRequest(request);
        return new ModelAndView("index.jspx");
      } else {
        /**
         * If no return address has been specified, we write the response
         * directly
         */
        ServiceDelivery delivery = _siriRepeaterService.handlServiceRequestWithResponse(request);
        _siriSerializationService.marshall(delivery, response.getWriter());
        return null;
      }
    } else if (object instanceof SubscriptionRequest) {
      SubscriptionRequest request = (SubscriptionRequest) object;
      _siriRepeaterService.handleSubscriptionRequest(request);
      return new ModelAndView("index.jspx");
    }

    throw new UnexpectedSiriObjectException(object);
  }

  @RequestMapping(value = "/siri-debug", method = {
      RequestMethod.GET, RequestMethod.POST})
  public ModelAndView debug(@RequestBody String body) throws IOException,
      SiriRepeaterException {

    Object object = _siriSerializationService.unmarshal(body);
    System.out.println(object);
    return new ModelAndView("index.jspx");
  }

  @ExceptionHandler(SiriRepeaterException.class)
  public ModelAndView handleSiriRepeaterException(SiriRepeaterException ex) {
    return new ModelAndView("exception.jspx");
  }

  /****
   * Private Method
   ****/

  private boolean hasAddress(ServiceRequest request) {
    return request.getAddress() != null
        && !request.getAddress().trim().isEmpty();
  }
}
