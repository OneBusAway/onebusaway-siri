package org.onebusaway.siri.core;

import java.io.IOException;

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

    _siriListener.handleRawRequest(req.getReader(), resp.getWriter());
  }
}
