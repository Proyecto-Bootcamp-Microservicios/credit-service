package com.bootcamp.ntt.credit_service.service.Impl;

import com.bootcamp.ntt.credit_service.client.CustomerClient;
import com.bootcamp.ntt.credit_service.exception.BusinessRuleException;
import com.bootcamp.ntt.credit_service.mapper.CreditMapper;
import com.bootcamp.ntt.credit_service.model.CreditRequest;
import com.bootcamp.ntt.credit_service.model.CreditResponse;
import com.bootcamp.ntt.credit_service.repository.CreditRepository;
import com.bootcamp.ntt.credit_service.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

  private final CreditRepository creditRepository;
  private final CreditMapper creditMapper;
  private final CustomerClient customerClient;


  @Override
  public Flux<CreditResponse> getAllCredits() {
    return creditRepository.findAll()
      .map(creditMapper::toResponse)
      .doOnComplete(() -> log.debug("All credits retrieved"));
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
  public Mono<CreditResponse> createCredit(CreditRequest creditRequest) {
    log.debug("Creating credit for customer: {}", creditRequest.getCustomerId());

    return customerClient.getCustomerType(creditRequest.getCustomerId())
      .flatMap(customerType -> validateCreditCreation(creditRequest.getCustomerId(), customerType.getType())
        .then(Mono.just(creditRequest))
        .map(request -> creditMapper.toEntity(request, customerType.getType())) // Pasamos el tipo
        .flatMap(creditRepository::save)
        .map(creditMapper::toResponse))
      .doOnSuccess(response -> log.debug("Credit created with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error creating credit: {}", error.getMessage()));
  }

  @Override
  public Mono<CreditResponse> updateCredit(String id, CreditRequest creditRequest) {
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

  @Override
  public Flux<CreditResponse> getActiveCredits(Boolean isActive) {
    return creditRepository.findByIsActive(isActive)
      .map(creditMapper::toResponse)
      .doOnComplete(()->log.debug("Active credits retrieved : {}", isActive))
      .doOnError(error->log.error("Error getting active credits : {}",error.getMessage()));
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
