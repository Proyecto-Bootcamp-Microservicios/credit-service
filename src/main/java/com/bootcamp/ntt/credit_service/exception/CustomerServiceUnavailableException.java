package com.bootcamp.ntt.credit_service.exception;

public class CustomerServiceUnavailableException extends RuntimeException {
  public CustomerServiceUnavailableException(String message) {
    super(message);
  }
}
