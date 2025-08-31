package com.bootcamp.ntt.credit_service.service.Impl;

import com.bootcamp.ntt.credit_service.client.CustomerClient;
import com.bootcamp.ntt.credit_service.entity.Credit;
import com.bootcamp.ntt.credit_service.exception.BusinessRuleException;
import com.bootcamp.ntt.credit_service.mapper.CreditMapper;
import com.bootcamp.ntt.credit_service.model.*;
import com.bootcamp.ntt.credit_service.repository.CreditRepository;
import com.bootcamp.ntt.credit_service.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;


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
  public Mono<CreditResponse> deactivateCredit(String id) {
    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + id)))
      .flatMap(credit -> {
        credit.setActive(false);  // soft delete
        return creditRepository.save(credit);
      })
      .map(creditMapper::toResponse)
      .doOnSuccess(c -> log.debug("Credit  {} deactivated", id))
      .doOnError(e -> log.error("Error deactivating credit credit {}: {}", id, e.getMessage()));
  }

  @Override
  public Mono<CreditResponse> activateCredit(String id) {
    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + id)))
      .flatMap(credit -> {
        credit.setActive(true);  // reactivar
        return creditRepository.save(credit);
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

  @Override
  public Mono<PaymentProcessResponse> processPayment(String creditNumber, PaymentProcessRequest paymentRequest) {
    log.debug("Processing payment for credit: {}, amount: {}", creditNumber, paymentRequest.getAmount());
    return creditRepository.findByCreditNumber(creditNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + creditNumber)))
      .flatMap(credit -> validateAndProcessPayment(credit, paymentRequest))
      .doOnSuccess(response -> {
        if (response.getSuccess()) {
          log.info("Payment processed successfully for credit {}: paid {}",
            creditNumber, response.getActualPaymentAmount());
        } else {
          log.warn("Payment failed for credit {}: {}", creditNumber, response.getErrorMessage());
        }
      })
      .doOnError(error -> log.error("Error processing payment for credit {}: {}", creditNumber, error.getMessage()));
  }
  @Override
  public Mono<CreditBalanceResponse> getCreditBalance(String creditNumber) {
    log.debug("Getting balance for credit: {}", creditNumber);

    return creditRepository.findByCreditNumber(creditNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + creditNumber)))
      .map(this::buildBalanceResponse)
      .doOnSuccess(response -> log.debug("Balance retrieved for credit: {}", creditNumber))
      .doOnError(error -> log.error("Error getting balance for credit {}: {}", creditNumber, error.getMessage()));
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

  private Mono<PaymentProcessResponse> validateAndProcessPayment(Credit credit, PaymentProcessRequest request) {
    BigDecimal paymentAmount = BigDecimal.valueOf(request.getAmount());
    // Validar estado del crédito
    if (!credit.isActive()) {
      return Mono.just(createPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.CREDIT_INACTIVE, "Credit is not active"));
    }

    // Validar monto
    if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return Mono.just(createPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.INVALID_AMOUNT, "Payment amount must be greater than 0"));
    }

    //  Validar balance cero
    if (credit.getCurrentBalance().compareTo(BigDecimal.ZERO) == 0) {
      return Mono.just(createPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.ZERO_CURRENT_BALANCE, "Credit has no outstanding balance"));
    }

    // Determinar monto real a pagar
    BigDecimal actualPaymentAmount = calculateActualPaymentAmount(credit, paymentAmount);

    // Calcular nuevos saldos
    //BigDecimal newCurrentBalance = credit.getCurrentBalance().subtract(actualPaymentAmount);
    BigDecimal newAvailableCredit = credit.getAvailableCredit().add(actualPaymentAmount);

    // Actualizar la tarjeta
    //credit.setCurrentBalance(newCurrentBalance);
    credit.setAvailableCredit(newAvailableCredit);

    return creditRepository.save(credit)
      .map(savedCredit -> createPaymentSuccessResponse(savedCredit, paymentAmount, actualPaymentAmount));
  }
  private BigDecimal calculateActualPaymentAmount(Credit credit, BigDecimal requestedAmount) {
    // Si el pago es mayor al balance, se paga solo lo que se debe
    if (requestedAmount.compareTo(credit.getCurrentBalance()) > 0) {
      log.info("Payment amount {} exceeds balance {}, adjusting to full balance",
        requestedAmount, credit.getCurrentBalance());
      return credit.getCurrentBalance();
    }
    return requestedAmount;
  }
  private PaymentProcessResponse createPaymentSuccessResponse(Credit credit, BigDecimal requestedAmount, BigDecimal actualAmount) {
    PaymentProcessResponse response = new PaymentProcessResponse();
    response.setSuccess(true);
    response.setCreditId(credit.getId());
    response.setRequestedAmount(requestedAmount.doubleValue());
    response.setActualPaymentAmount(actualAmount.doubleValue());
    response.setAvailableCreditAfter(credit.getAvailableCredit().doubleValue());
    //response.setCurrentBalanceAfter(credit.getCurrentBalance().doubleValue());
    response.setProcessedAt(OffsetDateTime.now());
    return response;
  }
  private PaymentProcessResponse createPaymentFailedResponse(String creditId, BigDecimal requestedAmount,
                                                             PaymentProcessResponse.ErrorCodeEnum errorCode, String errorMessage) {
    PaymentProcessResponse response = new PaymentProcessResponse();
    response.setSuccess(false);
    response.setCreditId(creditId);
    response.setRequestedAmount(requestedAmount.doubleValue());
    response.setErrorCode(errorCode);
    response.setErrorMessage(errorMessage);
    response.setProcessedAt(OffsetDateTime.now());
    return response;
  }
  private CreditBalanceResponse buildBalanceResponse(Credit credit) {
    // Calcular porcentaje de utilización
    BigDecimal utilizationPercentage = credit.getCreditLimit().compareTo(BigDecimal.ZERO) > 0
      ? 1//credit.getCurrentBalance()
      .multiply(BigDecimal.valueOf(100))
      .divide(credit.getCreditLimit(), 2, java.math.RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
    CreditBalanceResponse response = new CreditBalanceResponse();
    response.setCreditId(credit.getId());
    response.setCreditNumber(credit.getCreditNumber());
    response.setCreditLimit(credit.getCreditLimit().doubleValue());
    response.setAvailableCredit(credit.getAvailableCredit().doubleValue());
    //response.setCurrentBalance(credit.getCurrentBalance().doubleValue());
    response.setUtilizationPercentage(utilizationPercentage.doubleValue());
    response.setIsActive(credit.isActive());
    return response;
  }
}
