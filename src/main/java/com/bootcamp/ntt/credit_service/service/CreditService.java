package com.bootcamp.ntt.credit_service.service;

import com.bootcamp.ntt.credit_service.model.CreditCreateRequest;
import com.bootcamp.ntt.credit_service.model.CreditUpdateRequest;
import com.bootcamp.ntt.credit_service.model.CreditResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditService {

  Flux<CreditResponse> getAllCredits();

  Flux<CreditResponse> getCreditsByActive(Boolean isActive);

  //Flux<CreditResponse> getCreditsByCustomer(String customerId);

  Mono<CreditResponse> getCreditById(String id);

  Flux<CreditResponse> getCreditsByActiveAndCustomer(Boolean isActive, String customerId);

  Mono<CreditResponse> createCredit(CreditCreateRequest creditRequest);

  Mono<CreditResponse> updateCredit(String id, CreditUpdateRequest creditRequest);

  Mono<Void> deleteCredit(String id);

  Mono<CreditResponse> deactivateCard(String id);

  Mono<CreditResponse> activateCard(String id);

  //Flux<CreditResponse> getActiveCredits(Boolean isActive);


}
