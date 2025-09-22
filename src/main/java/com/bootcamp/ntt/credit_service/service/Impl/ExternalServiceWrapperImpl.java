package com.bootcamp.ntt.credit_service.service.Impl;

import com.bootcamp.ntt.credit_service.client.CardServiceClient;
import com.bootcamp.ntt.credit_service.client.CustomerServiceClient;
import com.bootcamp.ntt.credit_service.client.dto.card.CustomerEligibilityResponse;
import com.bootcamp.ntt.credit_service.client.dto.customer.CustomerTypeResponse;
import com.bootcamp.ntt.credit_service.exception.CustomerServiceUnavailableException;
import com.bootcamp.ntt.credit_service.service.ExternalServiceWrapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalServiceWrapperImpl implements ExternalServiceWrapper {

  private final CustomerServiceClient customerServiceClient;
  private final CardServiceClient cardServiceClient;

  private final CircuitBreaker customerServiceCircuitBreaker;
  private final CircuitBreaker cardServiceCircuitBreaker;

  private final TimeLimiter customerServiceTimeLimiter;
  private final TimeLimiter cardServiceTimeLimiter;

  /**
   * Llama al customer-service con circuit breaker y timeout de 2s
   */
  @Override
  public Mono<CustomerTypeResponse> getCustomerTypeWithCircuitBreaker(String customerId) {
    return customerServiceClient.getCustomerType(customerId)
      .transformDeferred(CircuitBreakerOperator.of(customerServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(customerServiceTimeLimiter))
      .doOnError(error -> log.warn("Customer service call failed for customerId={}: {}",
        customerId, error.getMessage()))
      .onErrorResume(this::handleCustomerServiceError);
  }

  /**
   * Llama al card-service para verificar elegibilidad con circuit breaker y timeout
   */
  @Override
  public Mono<CustomerEligibilityResponse> getCustomerEligibilityWithCircuitBreaker(String customerId, ServerWebExchange exchange) {
    log.debug("Calling card service for customer eligibility: {}", customerId);

    return cardServiceClient.getCustomerProductEligibility(customerId, exchange)
      .transformDeferred(CircuitBreakerOperator.of(cardServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(cardServiceTimeLimiter))
      .doOnSuccess(response -> log.debug("Card service eligibility response for customer {}: eligible={}",
        customerId, response.isEligible()))
      .doOnError(error -> log.warn("Card service call failed for customerId={}: {}",
        customerId, error.getMessage()))
      .onErrorResume(error -> handleCardServiceError(customerId, error));
  }

  private Mono<CustomerTypeResponse> handleCustomerServiceError(Throwable error) {
    log.error("Customer service unavailable - blocking credit creation for security: {}", error.getMessage());

    return Mono.error(new CustomerServiceUnavailableException(
      "Customer validation service temporarily unavailable. Credit creation blocked for security. Please try again later."));
  }

  private Mono<CustomerEligibilityResponse> handleCardServiceError(String customerId, Throwable error) {
    log.error("Card service unavailable for customer {} - using conservative fallback: {}",
      customerId, error.getMessage());

    CustomerEligibilityResponse fallbackResponse = new CustomerEligibilityResponse();
    fallbackResponse.setEligible(false);

    log.warn("Using conservative fallback for customer {}: marking as NOT eligible due to service unavailability",
      customerId);

    return Mono.just(fallbackResponse);
  }
}
