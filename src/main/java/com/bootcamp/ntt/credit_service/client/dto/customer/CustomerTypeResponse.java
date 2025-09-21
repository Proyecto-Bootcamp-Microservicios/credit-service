package com.bootcamp.ntt.credit_service.client.dto.customer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CustomerTypeResponse {
  @JsonProperty("id")
  private String id;

  @JsonProperty("customerType")
  private String customerType; // "PERSONAL" o "ENTERPRISE"
}
