package com.bootcamp.ntt.credit_service.service;

import com.bootcamp.ntt.credit_service.model.CreditRequest;
import com.bootcamp.ntt.credit_service.model.CreditResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public interface CreditService {

  Flux<CreditResponse> getAllCredits();

  Mono<CreditResponse> getCreditById(String id);

  Mono<CreditResponse> createCredit(CreditRequest creditRequest);

  Mono<CreditResponse> updateCredit(String id, CreditRequest creditRequest);

  Mono<Void> deleteCredit(String id);

  Flux<CreditResponse> getActiveCredits(Boolean isActive);


}
