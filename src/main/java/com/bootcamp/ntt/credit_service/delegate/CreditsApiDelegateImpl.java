package com.bootcamp.ntt.credit_service.delegate;

import com.bootcamp.ntt.credit_service.api.CreditsApiDelegate;
import com.bootcamp.ntt.credit_service.model.CreditCreateRequest;
import com.bootcamp.ntt.credit_service.model.CreditResponse;
import com.bootcamp.ntt.credit_service.model.CreditUpdateRequest;
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
    // El GlobalExceptionHandler maneja automáticamente:
    // - BusinessRuleException → HTTP 409
    // - CustomerNotFoundException → HTTP 404
    // - CustomerServiceException → HTTP 503
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
    // No necesita manejo de errores, el GlobalExceptionHandler se encarga
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
    // Solo manejamos el caso de "no encontrado" aquí,
    // otras excepciones las maneja el GlobalExceptionHandler
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
    // El GlobalExceptionHandler maneja "Credit not found" como HTTP 404
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
    // El GlobalExceptionHandler maneja "Credit not found" como HTTP 404
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
      .deactivateCard(id)
      .map(response -> {
        log.info("Credit deactivated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
    // El GlobalExceptionHandler maneja "Credit not found" como HTTP 404
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
      .activateCard(id)
      .map(response -> {
        log.info("Credit activated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
    // El GlobalExceptionHandler maneja "Credit not found" como HTTP 404
  }

  /**
   * Manejo de errores para el método createCredit (mantiene compatibilidad)
   * Este método solo se usa en createCredit por si hay algún error no manejado específicamente
   */
  private Mono<ResponseEntity<CreditResponse>> handleError(Throwable error) {
    log.error("Handling error in delegate: {}", error.getMessage(), error);

    // El GlobalExceptionHandler debería manejar la mayoría de casos,
    // esto es solo un fallback para casos no previstos
    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
  }
}
