package com.bootcamp.ntt.credit_service.exception;

public class CardServiceException extends RuntimeException{
  public CardServiceException(String message) {
    super(message);
  }
}
