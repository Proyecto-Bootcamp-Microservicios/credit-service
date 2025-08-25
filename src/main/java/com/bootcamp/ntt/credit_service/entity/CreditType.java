package com.bootcamp.ntt.credit_service.entity;

import lombok.ToString;

@ToString
public enum CreditType {
  PERSONAL,
  ENTERPRISE;

  public static CreditType fromString(String value) {
    if (value != null) {
      for (CreditType type : CreditType.values()) {
        if (type.name().equalsIgnoreCase(value)) {
          return type;
        }
      }
    }
    return null; // o excepcion
  }

}
