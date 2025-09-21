package com.bootcamp.ntt.credit_service.client.dto.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CustomerEligibilityResponse {
  @JsonProperty("isEligible")
  private boolean isEligible;
}
