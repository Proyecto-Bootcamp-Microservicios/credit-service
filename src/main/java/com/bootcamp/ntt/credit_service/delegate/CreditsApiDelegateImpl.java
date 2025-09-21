package com.bootcamp.ntt.credit_service.delegate;

import com.bootcamp.ntt.credit_service.api.CreditsApiDelegate;
import com.bootcamp.ntt.credit_service.exception.AccessDeniedException;
import com.bootcamp.ntt.credit_service.mapper.CreditMapper;
import com.bootcamp.ntt.credit_service.model.*;

import com.bootcamp.ntt.credit_service.service.CreditService;
import com.bootcamp.ntt.credit_service.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditsApiDelegateImpl implements CreditsApiDelegate {

  private final CreditService creditService;
  private final SecurityUtils securityUtils;
  private final CreditMapper creditMapper;

  /**
   * POST /credits : Create a new credit
   * Validates customer eligibility and creates installment-based credit
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> createCredit(
    Mono<CreditCreateRequest> creditRequest,
    ServerWebExchange exchange) {

    log.info("Creating new credit - Request received");

    return securityUtils.extractAuthHeaders(exchange)
      .doOnNext(auth -> log.debug("Auth extracted - customerId: {}, isAdmin: {}",
        auth.getCustomerId(), auth.isAdmin()))
      .zipWith(creditRequest.doOnNext(req -> log.debug("Original request customerId: {}",
        req.getCustomerId())))
      .flatMap(tuple -> {
        var auth = tuple.getT1();
        var request = tuple.getT2();

        CreditCreateRequest securedRequest = creditMapper.secureCreateRequest(
          request,
          auth.getCustomerId(),
          auth.isAdmin()
        );

        return creditService.createCredit(securedRequest);
      })
      .map(response -> {
        log.info("Credit created successfully with ID: {} - Monthly payment: {}",
          response.getId(), response.getMonthlyPayment());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      });
  }

  /**
   * GET /credits : Get all credits
   * Admins see all credits, customers see only their own
   */
  @Override
  public Mono<ResponseEntity<Flux<CreditResponse>>> getAllCredits(
    String customerId,
    Boolean isActive,
    ServerWebExchange exchange) {

    log.info("Getting credits - customerId: {}, isActive: {}", customerId, isActive);

    return securityUtils.extractAuthHeaders(exchange)
      .map(auth -> {
        Boolean activeFilter = Optional.ofNullable(isActive).orElse(true);
        String resolvedCustomerId = auth.isAdmin() ? customerId : auth.getCustomerId();

        log.info("Resolved access - isAdmin: {}, customerId: {}", auth.isAdmin(), resolvedCustomerId);

        Flux<CreditResponse> credits = (resolvedCustomerId != null)
          ? creditService.getCreditsByActiveAndCustomer(activeFilter, resolvedCustomerId)
          : creditService.getCreditsByActive(activeFilter);

        return ResponseEntity.ok(credits.doOnComplete(() ->
          log.info("Credits retrieved successfully for customer: {}", resolvedCustomerId)));
      });
  }

  /**
   * GET /credits/{id} : Get a credit by ID
   * Validates read access (admin or owner)
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> getCreditById(
    String id,
    ServerWebExchange exchange) {

    log.info("Getting credit by ID: {}", id);

    return securityUtils.validateReadAccess(
        creditService.getCreditById(id),
        CreditResponse::getCustomerId,
        exchange)
      .map(response -> {
        log.info("Credit found: {} %", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
  }

  /**
   * PUT /credits/{id} : Update a credit by ID
   * Admin only operation
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> updateCredit(
    String id,
    Mono<CreditUpdateRequest> creditRequest,
    ServerWebExchange exchange) {

    log.info("Updating credit with ID: {}", id);

    return securityUtils.validateAdminOnly(exchange)
      .then(creditRequest)
      .doOnNext(request -> log.info("Admin update request for credit ID: {}", id))
      .flatMap(request -> creditService.updateCredit(id, request))
      .map(response -> {
        log.info("Credit updated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * DELETE /credits/{id} : Delete a credit by ID
   * Admin only operation
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Deleting credit with ID: {}", id);

    return securityUtils.validateAdminOnly(exchange)
      .then(creditService.deleteCredit(id))
      .then(Mono.fromCallable(() -> {
        log.info("Credit deleted successfully: {}", id);
        return ResponseEntity.noContent().build();
      }));
  }

  /**
   * PATCH /credits/{id}/deactivate : Deactivate a credit
   * Admin only operation
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> deactivateCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Deactivating credit with ID: {}", id);

    return securityUtils.validateAdminOnly(exchange)
      .then(creditService.deactivateCredit(id))
      .map(response -> {
        log.info("Credit deactivated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * PATCH /credits/{id}/activate : Activate a credit
   * Admin only operation
   */
  @Override
  public Mono<ResponseEntity<CreditResponse>> activateCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Activating credit with ID: {}", id);

    return securityUtils.validateAdminOnly(exchange)
      .then(creditService.activateCredit(id))
      .map(response -> {
        log.info("Credit activated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * POST /credits/{creditNumber}/process-payment : Process installment payment
   * Customer can pay their own credit, admin can pay any credit
   */
  @Override
  public Mono<ResponseEntity<PaymentProcessResponse>> processCreditPayment(
    String creditNumber,
    Mono<PaymentProcessRequest> paymentProcessRequest,
    ServerWebExchange exchange) {

    log.info("Processing installment payment for credit: {}", creditNumber);

    return creditService.getCreditByNumber(creditNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found")))
      .flatMap(credit -> securityUtils.validateReadAccess(credit.getCustomerId(), exchange)
        .thenReturn(credit))
      .then(paymentProcessRequest)
      .doOnNext(request -> log.info("Payment request for credit {}: amount {} (expected monthly: {})",
        creditNumber, request.getAmount(), "calculating..."))
      .flatMap(request -> creditService.processPayment(creditNumber, request))
      .map(response -> {
        if (response.getSuccess()) {
          log.info("Installment payment processed successfully for credit {}: paid {} - Remaining installments: {}",
            creditNumber, response.getActualPaymentAmount(), response.getRemainingInstallments());
        } else {
          log.warn("Payment failed for credit {}: {}", creditNumber, response.getErrorMessage());
        }
        return ResponseEntity.ok(response);
      });
  }

  /**
   * GET /credits/{creditNumber}/balance : Get credit balance and payment info
   * Customer can view their own credit, admin can view any credit
   */
  @Override
  public Mono<ResponseEntity<CreditBalanceResponse>> getCreditBalance(
    String creditNumber,
    ServerWebExchange exchange) {

    log.info("Getting balance for credit: {}", creditNumber);

    return creditService.getCreditByNumber(creditNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found")))
      .flatMap(credit -> securityUtils.validateReadAccess(credit.getCustomerId(), exchange)
        .then(creditService.getCreditBalance(creditNumber)))
      .map(response -> {
        log.info("Balance retrieved for credit {}%",creditNumber);
        return ResponseEntity.ok(response);
      });
  }

  /**
   * GET /credits/eligibility/{customerId} : Check customer product eligibility
   * Customer can check their own eligibility, admin can check any customer
   */
  @Override
  public Mono<ResponseEntity<ProductEligibilityResponse>> checkCustomerEligibility(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Checking product eligibility for customer: {}", customerId);

    return securityUtils.extractAuthHeaders(exchange)
      .flatMap(auth -> {
        String resolvedCustomerId = auth.isAdmin() ? customerId : auth.getCustomerId();

        if (!auth.isAdmin() && !auth.hasCustomerId(customerId)) {
          return Mono.error(new AccessDeniedException("Access denied to eligibility check"));
        }

        return creditService.checkCustomerEligibility(resolvedCustomerId);
      })
      .map(response -> {
        log.info("Eligibility checked for customer: {} - Eligible: {} - Overdue products: {}",
          customerId, response.getIsEligible(), response.getOverdueProducts().size());
        return ResponseEntity.ok(response);
      });
  }


  /**
   * Helper method to get credit by creditNumber and validate access
   */
  private Mono<CreditResponse> getCreditWithAccessValidation(String creditNumber, ServerWebExchange exchange) {
    return creditService.getCreditByNumber(creditNumber) // You'll need this method
      .flatMap(credit -> securityUtils.validateReadAccess(credit.getCustomerId(), exchange)
        .thenReturn(credit));
  }
}
