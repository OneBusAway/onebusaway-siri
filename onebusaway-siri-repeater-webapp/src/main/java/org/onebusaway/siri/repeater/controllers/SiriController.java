package org.onebusaway.siri.repeater.controllers;

import javax.servlet.http.HttpServletResponse;

import org.onebusaway.siri.core.exceptions.SiriException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class SiriController {

  @RequestMapping(value = "/siri-consumer", method = {
      RequestMethod.GET, RequestMethod.POST})
  public ModelAndView consumer(@RequestBody String body) throws SiriException {

    return new ModelAndView("index.jspx");
  }

  @RequestMapping(value = "/siri-producer", method = {
      RequestMethod.GET, RequestMethod.POST})
  public ModelAndView producer(@RequestBody String body,
      HttpServletResponse response) throws SiriException {

    return new ModelAndView("index.jspx");
  }

  @RequestMapping(value = "/siri-debug", method = {
      RequestMethod.GET, RequestMethod.POST})
  public ModelAndView debug(@RequestBody String body) throws SiriException {

    return new ModelAndView("index.jspx");
  }

  @ExceptionHandler(SiriException.class)
  public ModelAndView handleSiriRepeaterException(SiriException ex) {

    return new ModelAndView("exception.jspx");
  }
}
