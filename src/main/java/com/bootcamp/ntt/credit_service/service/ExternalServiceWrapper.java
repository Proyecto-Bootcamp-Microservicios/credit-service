package com.bootcamp.ntt.credit_service.service;

import com.bootcamp.ntt.credit_service.client.dto.card.CustomerEligibilityResponse;
import com.bootcamp.ntt.credit_service.client.dto.customer.CustomerTypeResponse;
import reactor.core.publisher.Mono;

public interface ExternalServiceWrapper {
  Mono<CustomerTypeResponse> getCustomerTypeWithCircuitBreaker(String customerId);

  Mono<CustomerEligibilityResponse> getCustomerEligibilityWithCircuitBreaker(String customerId);
}
