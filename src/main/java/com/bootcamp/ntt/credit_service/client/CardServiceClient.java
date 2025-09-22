package com.bootcamp.ntt.credit_service.client;

import com.bootcamp.ntt.credit_service.client.dto.card.CustomerEligibilityResponse;
import com.bootcamp.ntt.credit_service.exception.CardServiceException;
import com.bootcamp.ntt.credit_service.exception.CustomerNotFoundException;
import com.bootcamp.ntt.credit_service.exception.CustomerServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardServiceClient {

  private final WebClient webClient;

  @Value("${services.card.service-name:card-service}")
  private String cardServiceUrl;

  public Mono<CustomerEligibilityResponse> getCustomerProductEligibility(
    String customerId,
    ServerWebExchange exchange) {

    String customerIdHeader = exchange.getRequest().getHeaders().getFirst("X-Customer-Id");
    String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");
    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

    return webClient
      .get()
      .uri( cardServiceUrl + "/api/v1/credit-cards/customers/{id}/product-eligibility", customerId)
      .header("X-Customer-Id", customerIdHeader)
      .header("X-User-Role", userRole)
      .header("X-User-Id", userId)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError,
        response -> {
          log.warn("Customer not found: {}", customerId );
          return Mono.error(new CustomerNotFoundException("Customer not found: " + customerId));
        })
      .onStatus(HttpStatus::is5xxServerError,
        response -> {
          log.error("Card service error for customer: {}", customerId);
          return Mono.error(new CardServiceException("Error communicating with card service"));
        })
      .bodyToMono(CustomerEligibilityResponse.class)
      .doOnSuccess(response -> log.debug("Customer eligible retrieved: {} for ID: {}",
        response.isEligible(), customerId))
      .doOnError(error -> log.error("Error fetching: {}", error.getMessage()));
  }

}
