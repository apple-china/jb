package com.jiabei.cloud.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class Trace {
  private Trace() {}
  public static String id(HttpServletRequest request) {
    Object current=request.getAttribute("traceId");
    if(current!=null) return current.toString();
    String id=UUID.randomUUID().toString(); request.setAttribute("traceId",id); return id;
  }
}
