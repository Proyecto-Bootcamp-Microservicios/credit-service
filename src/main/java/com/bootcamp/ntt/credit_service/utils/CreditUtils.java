package com.bootcamp.ntt.credit_service.utils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CreditUtils {
  private static final SecureRandom random = new SecureRandom();

  public String generateRandomCreditNumber() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      sb.append(random.nextInt(10));
    }
    return "CR-" + sb;
  }
}
