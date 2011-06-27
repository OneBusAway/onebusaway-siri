package org.onebusaway.siri.jetty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.onebusaway.siri.core.handlers.SiriRawHandler;

class SubscriptionServerServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

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

    BufferedReader reader = req.getReader();
    PrintWriter writer = resp.getWriter();
    
    _siriListener.handleRawRequest(reader, writer);
    
    reader.close();
    writer.close();
  }
}
