package com.jiabei.cloud.integration;

public class CardGatewayException extends RuntimeException {
  private final String code;private final boolean retryable;private final boolean unrecoverable;
  public CardGatewayException(String code,boolean retryable,boolean unrecoverable){super(code);this.code=code;this.retryable=retryable;this.unrecoverable=unrecoverable;}
  public String code(){return code;}public boolean retryable(){return retryable;}public boolean unrecoverable(){return unrecoverable;}
}
