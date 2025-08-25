package com.bootcamp.ntt.credit_service.repository;

import com.bootcamp.ntt.credit_service.entity.Credit;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CreditRepository extends ReactiveMongoRepository<Credit,String> {
  Flux<Credit> findByIsActive(Boolean isActive);
  Mono<Long> countByCustomerIdAndIsActiveTrue(String customerId);

}
