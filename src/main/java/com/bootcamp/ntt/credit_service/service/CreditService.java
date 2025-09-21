package com.bootcamp.ntt.credit_service.service;

import com.bootcamp.ntt.credit_service.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditService {


  Flux<CreditResponse> getCreditsByActive(Boolean isActive);

  Mono<CreditResponse> getCreditById(String id);

  Mono<CreditResponse> getCreditByNumber(String cardNumber);

  Flux<CreditResponse> getCreditsByActiveAndCustomer(Boolean isActive, String customerId);

  Mono<CreditResponse> createCredit(CreditCreateRequest creditRequest);

  Mono<ProductEligibilityResponse> checkCustomerEligibility(String customerId);

  Mono<CreditResponse> updateCredit(String id, CreditUpdateRequest creditRequest);

  Mono<Void> deleteCredit(String id);

  Mono<CreditResponse> deactivateCredit(String id);

  Mono<CreditResponse> activateCredit(String id);

  Mono<String> generateUniqueCreditNumber();

  Mono<PaymentProcessResponse> processPayment(String creditNumber, PaymentProcessRequest paymentRequest);

  Mono<CreditBalanceResponse> getCreditBalance(String cardNumber);


}
