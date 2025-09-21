package com.bootcamp.ntt.credit_service.service.Impl;

import com.bootcamp.ntt.credit_service.client.CustomerServiceClient;
import com.bootcamp.ntt.credit_service.entity.Credit;
import com.bootcamp.ntt.credit_service.entity.CreditStatus;
import com.bootcamp.ntt.credit_service.exception.BusinessRuleException;
import com.bootcamp.ntt.credit_service.mapper.CreditMapper;
import com.bootcamp.ntt.credit_service.model.*;
import com.bootcamp.ntt.credit_service.repository.CreditRepository;
import com.bootcamp.ntt.credit_service.service.CreditService;
import com.bootcamp.ntt.credit_service.service.ExternalServiceWrapper;
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
  private final CustomerServiceClient customerServiceClient;
  private final ExternalServiceWrapper externalServiceWrapper;
  private static final SecureRandom random = new SecureRandom();

  @Override
  public Mono<CreditResponse> getCreditById(String id) {
    log.debug("Getting credit by ID: {}", id);
    return creditRepository.findById(id)
      .doOnNext(this::updateCreditStatusIfNeeded)
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
  public Mono<CreditResponse> getCreditByNumber(String cardNumber) {
    log.debug("Getting credit by number: {}", cardNumber);
    return creditRepository.findByCreditNumber(cardNumber)
      .doOnNext(this::updateCreditStatusIfNeeded)
      .map(creditMapper::toResponse)
      .doOnSuccess(credit -> {
        if (credit != null) {
          log.debug("Credit found with number: {}", cardNumber);
        } else {
          log.debug("Credit not found with number: {}", cardNumber);
        }
      });
  }

  @Override
  public Mono<CreditResponse> createCredit(CreditCreateRequest creditRequest) {
    log.debug("Creating credit for customer: {} with amount: {}",
      creditRequest.getCustomerId(), creditRequest.getOriginalAmount());

    return externalServiceWrapper.getCustomerTypeWithCircuitBreaker(creditRequest.getCustomerId())
      .flatMap(customerType -> {
        log.debug("Customer type validated: {} for customer: {}",
          customerType.getCustomerType(), creditRequest.getCustomerId());

        return validateCreditCreation(creditRequest.getCustomerId(), customerType.getCustomerType())
          .then(externalServiceWrapper.getCustomerEligibilityWithCircuitBreaker(creditRequest.getCustomerId()))
          .flatMap(eligibilityResponse -> {
            if (!eligibilityResponse.isEligible()) {
              log.warn("Customer {} not eligible for credit due to overdue debt.",
                creditRequest.getCustomerId());

              return Mono.error(new BusinessRuleException(
                "CUSTOMER_HAS_OVERDUE_DEBT",
                "Customer cannot acquire new products due to overdue debt"
              ));
            }

            log.debug("Customer {} is eligible for new credit products", creditRequest.getCustomerId());
            return Mono.just(customerType.getCustomerType());
          })
          .then(generateUniqueCreditNumber())
          .map(creditNumber -> {
            Credit credit = creditMapper.toEntity(creditRequest, customerType.getCustomerType(), creditNumber);
            credit.initializeNewCredit();
            log.debug("Credit entity created - number: {}, monthly payment: {}",
              creditNumber, credit.getMonthlyPayment());
            return credit;
          })
          .flatMap(creditRepository::save)
          .map(creditMapper::toResponse);
      })
      .doOnSuccess(response -> log.info("Credit created successfully - ID: {}, Customer: {}, Monthly payment: {}, Total installments: {}",
        response.getId(), response.getCustomerId(), response.getMonthlyPayment(), response.getTotalInstallments()))
      .doOnError(error -> log.error("Error creating credit for customer {}: {}",
        creditRequest.getCustomerId(), error.getMessage()));
  }

  @Override
  public Mono<CreditResponse> updateCredit(String id, CreditUpdateRequest creditRequest) {
    log.debug("Updating credit with ID: {}", id);

    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found")))
      .map(existing -> creditMapper.updateEntity(existing, creditRequest))
      .doOnNext(this::updateCreditStatusIfNeeded)
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
  public Flux<CreditResponse> getCreditsByActive(Boolean isActive) {
    return creditRepository.findByIsActive(isActive)
      .doOnNext(this::updateCreditStatusIfNeeded)
      .map(creditMapper::toResponse)
      .doOnComplete(() -> log.debug("Active credits retrieved"));
  }

  @Override
  public Flux<CreditResponse> getCreditsByActiveAndCustomer(Boolean isActive, String customerId) {
    return creditRepository.findByIsActiveAndCustomerId(isActive, customerId)
      .doOnNext(this::updateCreditStatusIfNeeded)
      .map(creditMapper::toResponse)
      .doOnComplete(() -> log.debug("Credits active by customer retrieved"));
  }

  @Override
  public Mono<CreditResponse> deactivateCredit(String id) {
    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + id)))
      .flatMap(credit -> {
        credit.setActive(false);
        return creditRepository.save(credit);
      })
      .map(creditMapper::toResponse)
      .doOnSuccess(c -> log.debug("Credit {} deactivated", id))
      .doOnError(e -> log.error("Error deactivating credit {}: {}", id, e.getMessage()));
  }

  @Override
  public Mono<CreditResponse> activateCredit(String id) {
    return creditRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with id: " + id)))
      .flatMap(credit -> {
        credit.setActive(true);
        return creditRepository.save(credit);
      })
      .map(creditMapper::toResponse)
      .doOnSuccess(c -> log.debug("Credit {} activated", id))
      .doOnError(e -> log.error("Error activating credit {}: {}", id, e.getMessage()));
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
    log.debug("Processing installment payment for credit: {}, amount: {}",
      creditNumber, paymentRequest.getAmount());

    return creditRepository.findByCreditNumber(creditNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with number: " + creditNumber)))
      .doOnNext(this::updateCreditStatusIfNeeded)
      .flatMap(credit -> validateAndProcessInstallmentPayment(credit, paymentRequest))
      .doOnSuccess(response -> {
        if (response.getSuccess()) {
          log.info("Installment payment processed successfully for credit {}: paid {} - Remaining installments: {}",
            creditNumber, response.getActualPaymentAmount(), response.getRemainingInstallments());
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
      .switchIfEmpty(Mono.error(new RuntimeException("Credit not found with number: " + creditNumber)))
      .doOnNext(this::updateCreditStatusIfNeeded)
      .map(this::buildInstallmentBalanceResponse)
      .doOnSuccess(response -> log.debug("Balance retrieved for credit: {} - Progress: {}%",
        creditNumber, response.getPaymentProgress()))
      .doOnError(error -> log.error("Error getting balance for credit {}: {}", creditNumber, error.getMessage()));
  }

  @Override
  public Mono<ProductEligibilityResponse> checkCustomerEligibility(String customerId) {
    log.debug("Checking product eligibility for customer: {}", customerId);

    return getOverdueCredits(customerId)
      .collectList()
      .map(overdueCredits -> buildEligibilityResponse(customerId, overdueCredits))
      .doOnSuccess(response -> log.debug("Eligibility checked for customer: {} - Eligible: {}",
        customerId, response.getIsEligible()));
  }

  private Flux<OverdueProduct> getOverdueCredits(String customerId) {
    return creditRepository.findByCustomerId(customerId)
      .doOnNext(this::updateCreditStatusIfNeeded)
      .filter(credit -> Boolean.TRUE.equals(credit.getIsOverdue()))
      .map(this::mapCreditToOverdueProduct);
  }

  private String generateRandomCreditNumber() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      sb.append(random.nextInt(10));
    }
    return "CR-" + sb;
  }

  private Mono<Void> validateCreditCreation(String customerId, String customerType) {
    // Validar reglas de negocio según el tipo
    if ("PERSONAL".equals(customerType)) {
      return validatePersonalCreditRules(customerId);
    } else {
      return validateEnterpriseCreditRules();
    }
  }

  private Mono<Void> validatePersonalCreditRules(String customerId) {
    return creditRepository.countByCustomerIdAndIsActiveTrueAndStatus(customerId, CreditStatus.ACTIVE)
      .flatMap(activeCredits -> {
        if (activeCredits > 0) {
          return Mono.error(new BusinessRuleException(
            "PERSON_ALREADY_HAS_CREDIT",
            "Customers can only have one active credit that has not yet been paid."
          ));
        }
        return Mono.empty();
      });
  }

  private Mono<Void> validateEnterpriseCreditRules() {
    return Mono.empty();
  }

  private void updateCreditStatusIfNeeded(Credit credit) {
    // Actualizar estado de morosidad automáticamente
    credit.updateOverdueStatus();
  }

  private Mono<PaymentProcessResponse> validateAndProcessInstallmentPayment(Credit credit, PaymentProcessRequest request) {
    BigDecimal paymentAmount = BigDecimal.valueOf(request.getAmount());

    // Validar estado del crédito
    if (!credit.isActive()) {
      return Mono.just(createInstallmentPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.CREDIT_INACTIVE, "Credit is not active"));
    }

    // Validar monto positivo
    if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return Mono.just(createInstallmentPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.INVALID_AMOUNT, "Payment amount must be greater than 0"));
    }

    // Validar que el crédito no esté completamente pagado
    if (credit.getRemainingInstallments() == 0) {
      return Mono.just(createInstallmentPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.CREDIT_ALREADY_PAID, "Credit is already fully paid"));
    }

    // Validar que el pago cubra la cuota mensual mínima
    if (paymentAmount.compareTo(credit.getMonthlyPayment()) < 0) {
      return Mono.just(createInstallmentPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.INSUFFICIENT_PAYMENT,
        "Payment amount is less than monthly installment of " + credit.getMonthlyPayment()));
    }

    // Procesar el pago
    boolean paymentProcessed = credit.processPayment(paymentAmount);

    if (!paymentProcessed) {
      return Mono.just(createInstallmentPaymentFailedResponse(credit.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.INVALID_AMOUNT, "Payment processing failed"));
    }

    return creditRepository.save(credit)
      .map(savedCredit -> createInstallmentPaymentSuccessResponse(savedCredit, paymentAmount));
  }

  private PaymentProcessResponse createInstallmentPaymentSuccessResponse(Credit credit, BigDecimal requestedAmount) {
    PaymentProcessResponse response = new PaymentProcessResponse();
    response.setSuccess(true);
    response.setCreditId(credit.getId());
    response.setRequestedAmount(requestedAmount.doubleValue());
    response.setActualPaymentAmount(credit.getMonthlyPayment().doubleValue());
    response.setRemainingBalance(credit.getCurrentBalance().doubleValue());
    response.setPaidInstallments(credit.getPaidInstallments());
    response.setRemainingInstallments(credit.getRemainingInstallments());
    response.setNextPaymentDueDate(credit.getNextPaymentDueDate());
    response.setProcessedAt(OffsetDateTime.now());
    return response;
  }

  private PaymentProcessResponse createInstallmentPaymentFailedResponse(String creditId, BigDecimal requestedAmount,
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

  private CreditBalanceResponse buildInstallmentBalanceResponse(Credit credit) {
    CreditBalanceResponse response = new CreditBalanceResponse();
    response.setCreditId(credit.getId());
    response.setCreditNumber(credit.getCreditNumber());
    response.setOriginalAmount(credit.getOriginalAmount().doubleValue());
    response.setCurrentBalance(credit.getCurrentBalance().doubleValue());
    response.setMonthlyPayment(credit.getMonthlyPayment().doubleValue());
    response.setNextPaymentDueDate(credit.getNextPaymentDueDate());
    response.setPaidInstallments(credit.getPaidInstallments());
    response.setRemainingInstallments(credit.getRemainingInstallments());
    response.setPaymentProgress(credit.getPaymentProgress().doubleValue());
    response.setIsOverdue(credit.getIsOverdue());
    response.setOverdueDays(credit.getOverdueDays());
    response.setStatus(CreditBalanceResponse.StatusEnum.fromValue(credit.getStatus().name()));
    response.setIsActive(credit.isActive());
    return response;
  }

  private ProductEligibilityResponse buildEligibilityResponse(String customerId, java.util.List<OverdueProduct> overdueCredits) {
    ProductEligibilityResponse response = new ProductEligibilityResponse();
    response.setCustomerId(customerId);
    response.setIsEligible(overdueCredits.isEmpty());
    response.setOverdueProducts(overdueCredits);
    response.setCheckedAt(OffsetDateTime.now());

    if (!overdueCredits.isEmpty()) {
      response.setReason("Customer has overdue debt in credit products");
    }

    return response;
  }

  private OverdueProduct mapCreditToOverdueProduct(Credit credit) {
    OverdueProduct product = new OverdueProduct();
    product.setProductId(credit.getId());
    product.setProductNumber(credit.getCreditNumber());
    product.setProductType(OverdueProduct.ProductTypeEnum.CREDIT);
    product.setOverdueDays(credit.getOverdueDays());
    product.setOverdueAmount(credit.getMonthlyPayment().doubleValue());
    return product;
  }
}
