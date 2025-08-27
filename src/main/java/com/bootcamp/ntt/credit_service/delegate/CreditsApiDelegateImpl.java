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
    Mono<CreditCreateRequest>creditRequest,
    ServerWebExchange exchange) {

    log.info("Creating new credit - Request received");

    return creditRequest
      .doOnNext(request -> log.info("Creating credit for customer: {}", request.getCustomerId()))
      .flatMap(creditService::createCredit)
      .map(response -> {
        log.info("Credit created successfully with ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      })
      .doOnError(error -> log.error("Error creating credit: {}", error.getMessage(), error))
      .onErrorResume(this::handleError);
  }

  /**
   * GET /credits : Get all credits
   */
  @Override
  public Mono<ResponseEntity<Flux<CreditResponse>>> getAllCredits(String customerId, Boolean isActive, ServerWebExchange exchange) {

    Boolean activeFilter = (isActive != null) ? isActive : true;

    Flux<CreditResponse> credits;

    if (customerId != null) {
      credits = creditService.getCreditsByActiveAndCustomer(activeFilter, customerId);
    } else {
      credits = creditService.getCreditsByActive(activeFilter);
    }

    credits = credits
      .doOnComplete(() -> log.info("Credits retrieved successfully"))
      .doOnError(error -> log.error("Error getting credits: {}", error.getMessage(), error));

    return Mono.just(ResponseEntity.ok(credits))
      .onErrorResume(error -> {
        log.error("Exception in getAllCredits: {}", error.getMessage(), error);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Flux.empty()));
      });
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
      }))
      .doOnError(error -> log.error("Error getting credit by ID {}: {}", id, error.getMessage(), error))
      .onErrorResume(this::handleError);
  }

  /**
   * GET /credits/active?isActive=true : Get active credits
   */
  /*@Override
  public Mono<ResponseEntity<Flux<CreditResponse>>> getActiveCredits(
    Boolean isActive,
    String customerId,
    ServerWebExchange exchange) {

    log.info("Getting credits with isActive: {}", isActive);

    Flux<CreditResponse> credits = creditService
      .getActiveCredits(isActive)  // ✅ Método del service
      .doOnComplete(() -> log.info("Active credits retrieved successfully"))
      .doOnError(error -> log.error("Error getting active credits: {}", error.getMessage(), error));

    return Mono.just(ResponseEntity.ok(credits))
      .onErrorResume(error -> {
        log.error("Exception in getActiveCredits: {}", error.getMessage(), error);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Flux.empty()));
      });
  }*/

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
      .flatMap(request -> creditService.updateCredit(id, request))  // ✅ Método del service
      .map(response -> {
        log.info("Credit updated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Credit not found for update with ID: {}", id);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error updating credit {}: {}", id, error.getMessage(), error))
      .onErrorResume(this::handleError);
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
      .deleteCredit(id)  // Retorna Mono<Void>
      .then(Mono.just(ResponseEntity.noContent().<Void>build()))  // ✅ Tipo explícito aquí
      .doOnSuccess(response -> log.info("Credit deleted successfully: {}", id))
      .onErrorResume(error -> {
        log.error("Error deleting credit {}: {}", id, error.getMessage(), error);
        if (isNotFoundError(error)) {
          return Mono.just(ResponseEntity.notFound().build());  // ✅ Y aquí
        }
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());  // ✅ Y aquí
      });
  }

  @Override
  public Mono<ResponseEntity<CreditResponse>> deactivateCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Deactivating Credit with ID: {}", id);

    return creditService.deactivateCard(id)
      .map(response -> {
        log.info("Credit deactivated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Credit not found with ID: {}", id);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error deactivating Credit {}: {}", id, error.getMessage()))
      .onErrorResume(this::handleError);
  }

  @Override
  public Mono<ResponseEntity<CreditResponse>> activateCredit(
    String id,
    ServerWebExchange exchange) {

    log.info("Activating Credit with ID: {}", id);

    return creditService.activateCard(id)
      .map(response -> {
        log.info("Credit activated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Credit not found with ID: {}", id);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error activating card {}: {}", id, error.getMessage()))
      .onErrorResume(this::handleError);
  }

  /**
   * Manejo centralizado de errores para operaciones que retornan CreditResponse
   */
  private Mono<ResponseEntity<CreditResponse>> handleError(Throwable error) {
    log.error("Handling error: {}", error.getMessage(), error);

    if (isNotFoundError(error)) {
      return Mono.just(ResponseEntity.notFound().build());
    }

    if (isValidationError(error)) {
      return Mono.just(ResponseEntity.badRequest().build());
    }

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
  }

  /**
   * Verifica si el error es de tipo "not found"
   */
  private boolean isNotFoundError(Throwable error) {
    return error.getMessage() != null &&
      (error.getMessage().contains("not found") ||
        error.getMessage().contains("Not Found") ||
        error instanceof RuntimeException && error.getMessage().contains("404"));
  }

  /**
   * Verifica si el error es de validación
   */
  private boolean isValidationError(Throwable error) {
    return error.getMessage() != null &&
      (error.getMessage().contains("validation") ||
        error.getMessage().contains("invalid") ||
        error instanceof IllegalArgumentException);
  }

}
