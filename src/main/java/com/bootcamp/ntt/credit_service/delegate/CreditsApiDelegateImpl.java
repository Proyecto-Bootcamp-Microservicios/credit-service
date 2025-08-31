package com.bootcamp.ntt.credit_service.delegate;

import com.bootcamp.ntt.credit_service.api.CreditsApiDelegate;
import com.bootcamp.ntt.credit_service.model.*;

import com.bootcamp.ntt.credit_service.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditsApiDelegateImpl implements CreditsApiDelegate {

  private final CreditService creditService;

  /**
   * POST /credits : Create a new credit
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> createCredit(
    Mono<CreditCreateRequest> creditRequest,
    ServerWebExchange exchange) {

    log.info("Creating new credit - Request received");

    return creditRequest
      .doOnNext(request -> log.info("Creating credit for customer: {}", request.getCustomerId()))
      .flatMap(creditService::createCredit)
      .map(response -> {
        log.info("Credit created successfully with ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      });
  }

  /**
   * GET /credits : Get all credits
   */
  @Override
  public Mono<ResponseEntity<Flux<CreditResponse>>> getAllCredits(
    String customerId,
    Boolean isActive,
    ServerWebExchange exchange) {

    log.info("Getting credits - customerId: {}, isActive: {}", customerId, isActive);

    Boolean activeFilter = (isActive != null) ? isActive : true;

    Flux<CreditResponse> credits;

    if (customerId != null) {
      log.info("Filtering by customer ID: {} and active: {}", customerId, activeFilter);
      credits = creditService.getCreditsByActiveAndCustomer(activeFilter, customerId);
    } else {
      log.info("Getting all credits with active filter: {}", activeFilter);
      credits = creditService.getCreditsByActive(activeFilter);
    }

    credits = credits.doOnComplete(() -> log.info("Credits retrieved successfully"));

    return Mono.just(ResponseEntity.ok(credits));
  }

  /**
   * GET /credits/{id} : Get a credit by ID
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> getCreditById(
    String id,
    ServerWebExchange exchange) {

    log.info("Getting credit by ID: {}", id);

    return creditService
      .getCreditById(id)
      .map(response -> {
        log.info("Credit found: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Credit not found with ID: {}", id);
        return ResponseEntity.notFound().build();
      }));
  }

  /**
   * PUT /credits/{id} : Update a credit by ID
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> updateCredit(
    String id,
    Mono<CreditUpdateRequest> creditRequest,
    ServerWebExchange exchange) {

    log.info("Updating credit with ID: {}", id);

    return creditRequest
      .doOnNext(request -> log.info("Update request for credit ID: {}", id))
      .flatMap(request -> creditService.updateCredit(id, request))
      .map(response -> {
        log.info("Credit updated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * DELETE /credits/{id} : Delete a credit by ID
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Deleting credit with ID: {}", id);

    return creditService
      .deleteCredit(id)
      .then(Mono.fromCallable(() -> {
        log.info("Credit deleted successfully: {}", id);
        return ResponseEntity.noContent().build();
      }));
  }

  /**
   * PUT /credits/{id}/deactivate : Deactivate a credit
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> deactivateCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Deactivating credit with ID: {}", id);

    return creditService
      .deactivateCredit(id)
      .map(response -> {
        log.info("Credit deactivated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * PUT /credits/{id}/activate : Activate a credit
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> activateCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Activating credit with ID: {}", id);

    return creditService
      .activateCredit(id)
      .map(response -> {
        log.info("Credit activated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**

   POST /credits/{creditNumber}/process-payment : Process credit payment
   */
  @Override
  public Mono<ResponseEntity<PaymentProcessResponse>> processCreditPayment(
    String creditNumber,
    Mono<PaymentProcessRequest> paymentProcessRequest,
    ServerWebExchange exchange) {
    log.info("Processing payment for credit: {}", creditNumber);
    return paymentProcessRequest
      .doOnNext(request -> log.info("Payment request for credit {}: amount {}", creditNumber, request.getAmount()))
      .flatMap(request -> creditService.processPayment(creditNumber, request))
      .map(response -> {
        if (response.getSuccess()) {
          log.info("Payment processed successfully for credit {}: paid {}",
            creditNumber, response.getActualPaymentAmount());
        } else {
          log.warn("Payment failed for credit {}: {}", creditNumber, response.getErrorMessage());
        }
        return ResponseEntity.ok(response);
      });
  }

  /**

   GET /credits/{id}/balance : Get credit balance
   */
  @Override
  public Mono<ResponseEntity<CreditBalanceResponse>> getCreditBalance(
    String creditNumber,
    ServerWebExchange exchange) {
    log.info("Getting balance for credit: {}", creditNumber);
    return creditService.getCreditBalance(creditNumber)
      .map(response -> {
        log.info("Balance retrieved for credit {}: available {}, current {}",
          creditNumber, response.getAvailableCredit(), response.getCurrentBalance());
        return ResponseEntity.ok(response);
      });
  }

}
