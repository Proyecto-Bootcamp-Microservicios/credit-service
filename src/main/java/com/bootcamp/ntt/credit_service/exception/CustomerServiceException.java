package com.bootcamp.ntt.credit_service.exception;

public class CustomerServiceException extends RuntimeException{
  public CustomerServiceException(String message) {
    super(message);
  }
}
