package org.onebusaway.siri.jetty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.onebusaway.siri.core.handlers.SiriRawHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SubscriptionServerServlet extends HttpServlet {
  
  private static final long serialVersionUID = 1L;

  private static final Logger _log = LoggerFactory.getLogger(SubscriptionServerServlet.class);

  private SiriRawHandler _siriListener;

  public void setSiriListener(SiriRawHandler siriListener) {
    _siriListener = siriListener;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    
    _log.debug("path: {}", req.getRequestURI());

    BufferedReader reader = req.getReader();
    PrintWriter writer = resp.getWriter();
    
    _siriListener.handleRawRequest(reader, writer);
    
    reader.close();
    writer.close();
  }
}
