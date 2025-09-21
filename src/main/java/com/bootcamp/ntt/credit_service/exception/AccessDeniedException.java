package com.bootcamp.ntt.credit_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AccessDeniedException extends ResponseStatusException {
  public AccessDeniedException(String reason) {
    super(HttpStatus.FORBIDDEN, reason);
  }
}
