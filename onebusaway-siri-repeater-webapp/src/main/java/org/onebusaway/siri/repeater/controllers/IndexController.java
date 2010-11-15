package org.onebusaway.siri.repeater.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/index.do")
public class IndexController {

  @RequestMapping()
  public ModelAndView index() {
    return new ModelAndView("index.jspx");
  }
}
