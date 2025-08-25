package com.bootcamp.ntt.credit_service.exception;

public class BusinessRuleException extends RuntimeException{
  private final String code;

  public BusinessRuleException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
