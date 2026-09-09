package com.jiabei.cloud.web;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final Object details;
  public BusinessException(HttpStatus status, String code, String message) { this(status, code, message, null); }
  public BusinessException(HttpStatus status, String code, String message, Object details) { super(message); this.status=status; this.code=code; this.details=details; }
  public HttpStatus status(){ return status; }
  public String code(){ return code; }
  public Object details(){ return details; }
  public static BusinessException unauthorized(){ return new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","登录状态已失效，请重新进入。"); }
  public static BusinessException forbidden(){ return new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","你没有执行此操作的权限。"); }
}
