package com.bootcamp.ntt.credit_service.service.Impl;

import com.bootcamp.ntt.credit_service.client.CustomerClient;
import com.bootcamp.ntt.credit_service.entity.Credit;
import com.bootcamp.ntt.credit_service.exception.BusinessRuleException;
import com.bootcamp.ntt.credit_service.mapper.CreditMapper;
import com.bootcamp.ntt.credit_service.model.CreditCreateRequest;
import com.bootcamp.ntt.credit_service.model.CreditResponse;
import com.bootcamp.ntt.credit_service.model.CreditUpdateRequest;
import com.bootcamp.ntt.credit_service.repository.CreditRepository;
import com.bootcamp.ntt.credit_service.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;


@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

  private final CreditRepository creditRepository;
  private final CreditMapper creditMapper;
  private final CustomerClient customerClient;
  private static final SecureRandom random = new SecureRandom();


  @Override
  public Flux<CreditResponse> getAllCredits() {
    return creditRepository.findAll()
      .map(creditMapper::toResponse)
      .doOnComplete(() -> log.debug("Credits retrieved"));
  }

  @Override
  public Mono<CreditResponse> getCreditById(String id) {
    log.debug("Getting credit by ID: {}", id);
    return creditRepository.findById(id)
      .map(creditMapper::toResponse)
      .doOnSuccess(credit -> {
        if (credit != null) {
          log.debug("Credit found with ID: {}", id);
        } else {
          log.debug("Credit not found with ID: {}", id);
        }
      });
  }

  @Override
  public Mono<CreditResponse> createCredit(CreditCreateRequest creditRequest) {
    log.debug("Creating credit for customer: {}", creditRequest.getCustomerId());

    return customerClient.getCustomerType(creditRequest.getCustomerId())
      .flatMap(customerType -> validateCreditCreation(creditRequest.getCustomerId(), customerType.getCustomerType())
        //.then(Mono.just(creditRequest))
        .then(generateUniqueCreditNumber())
        .map(creditNumber -> creditMapper.toEntity(creditRequest, customerType.getCustomerType(),creditNumber)) // Pasamos el tipo
        .flatMap(creditRepository::save)
        .map(creditMapper::toResponse))
      .doOnSuccess(response -> log.debug("Credit created with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error creating credit: {}", error.getMessage()));
  }

  @Override
  public Mono<CreditResponse> updateCredit(String id, CreditUpdateRequest creditRequest) {
    log.debug("Updating credit with ID: {}", id);

    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found")))
      .map(existing -> creditMapper.updateEntity(existing, creditRequest))
      .flatMap(creditRepository::save)
      .map(creditMapper::toResponse)
      .doOnSuccess(response -> log.debug("Credit updated with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error updating credit {}: {}", id, error.getMessage()));
  }

  @Override
  public Mono<Void> deleteCredit(String id) {
    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found")))
      .flatMap(creditRepository::delete)
      .doOnSuccess(unused -> log.debug("Credit deleted"))
      .doOnError(error -> log.error("Error deleting credit {}: {}", id, error.getMessage()));
  }

  /*Override
  public Flux<CreditResponse> getActiveCredits(Boolean isActive) {
    return creditRepository.findByIsActive(isActive)
      .map(creditMapper::toResponse)
      .doOnComplete(()->log.debug("Active credits retrieved : {}", isActive))
      .doOnError(error->log.error("Error getting active credits : {}",error.getMessage()));
  }*/

  @Override
  public Flux<CreditResponse> getCreditsByActive(Boolean isActive) {
    return creditRepository.findByIsActive(isActive)
      .map(creditMapper::toResponse)
      .doOnComplete(() -> log.debug("Active credits retrieved"));
  }

  /*@Override
  public Flux<CreditResponse> getCreditsByCustomer(String customerId) {
    return creditRepository.findByCustomerId(customerId)
      .map(creditMapper::toResponse)
      .doOnComplete(() -> log.debug("Customer credits retrieved"));
  }*/

  @Override
  public Flux<CreditResponse> getCreditsByActiveAndCustomer(Boolean isActive, String customerId) {
    return creditRepository.findByIsActiveAndCustomerId(isActive, customerId)
      .map(creditMapper::toResponse)
      .doOnComplete(() -> log.debug("Credits active by customer retrieved "));
  }

  @Override
  public Mono<CreditResponse> deactivateCard(String id) {
    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(false);  // soft delete
        return creditRepository.save(card);
      })
      .map(creditMapper::toResponse)
      .doOnSuccess(c -> log.debug("Credit  {} deactivated", id))
      .doOnError(e -> log.error("Error deactivating credit card {}: {}", id, e.getMessage()));
  }

  @Override
  public Mono<CreditResponse> activateCard(String id) {
    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(true);  // reactivar
        return creditRepository.save(card);
      })
      .map(creditMapper::toResponse)
      .doOnSuccess(c -> log.debug("Credit {} activated", id))
      .doOnError(e -> log.error("Error activating credit  {}: {}", id, e.getMessage()));
  }

  @Override
  public Mono<String> generateUniqueCreditNumber() {
    String candidate = generateRandomCreditNumber();

    return creditRepository.findByCreditNumber(candidate)
      .flatMap(existing -> generateUniqueCreditNumber()) // si existe, intenta de nuevo
      .switchIfEmpty(Mono.just(candidate)); // si no existe, úsalo
  }

  private String generateRandomCreditNumber() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      sb.append(random.nextInt(10));
    }
    return sb.toString();
  }

  //Validaciones
  private Mono<Void> validateCreditCreation(String customerId, String customerType) {
    // Validar reglas de negocio según el tipo
    if ("PERSONAL".equals(customerType)) {
      return validatePersonalCreditRules(customerId);
    } else {
      return validateEnterpriseCreditRules();
    }
  }

  private Mono<Void> validatePersonalCreditRules(String customerId) {
    return creditRepository.countByCustomerIdAndIsActiveTrue(customerId)
      .flatMap(activeCredits -> {
        if (activeCredits > 0) {
          return Mono.error(new BusinessRuleException(
            "PERSON_ALREADY_HAS_CREDIT",
            "Personal customers can only have one active credit"
          ));
        }
        return Mono.empty();
      });
  }

  private Mono<Void> validateEnterpriseCreditRules(/*String customerId*/) {
    // Para empresariales no hay límite
    return Mono.empty();
  }
}
