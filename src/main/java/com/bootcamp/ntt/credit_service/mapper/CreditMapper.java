package com.bootcamp.ntt.credit_service.mapper;

import com.bootcamp.ntt.credit_service.entity.Credit;
import com.bootcamp.ntt.credit_service.entity.CreditType;
import com.bootcamp.ntt.credit_service.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class CreditMapper {

  /**
   * Convierte CreditCreateRequest a entidad Credit
   * Nota: Solo mapea campos básicos, initializeNewCredit() se llama en el service
   */
  public Credit toEntity(CreditCreateRequest dto, String customerType, String creditNumber) {
    if (dto == null) {
      return null;
    }

    Credit credit = new Credit();
    credit.setCreditNumber(creditNumber);
    credit.setCustomerId(dto.getCustomerId());
    credit.setType(CreditType.valueOf(customerType));
    credit.setOriginalAmount(BigDecimal.valueOf(dto.getOriginalAmount()));

    log.debug("Mapped create request - customer: {}, type: {}, amount: {}",
      dto.getCustomerId(), customerType, dto.getOriginalAmount());

    return credit;
  }

  /**
   * Actualiza entidad Credit existente con CreditUpdateRequest
   * Solo permite actualizar campos seguros para préstamos
   */
  public Credit updateEntity(Credit existing, CreditUpdateRequest dto) {
    if (existing == null || dto == null) {
      return existing;
    }

    if (dto.getNextPaymentDueDate() != null) {
      existing.setNextPaymentDueDate(dto.getNextPaymentDueDate());
      log.debug("Updated next payment due date to: {}", dto.getNextPaymentDueDate());
    }

    if (dto.getIsActive() != null) {
      existing.setActive(dto.getIsActive());
      log.debug("Updated active status to: {}", dto.getIsActive());
    }

    existing.updateOverdueStatus();

    return existing;
  }

  /**
   * Convierte entidad Credit a CreditResponse
   */
  public CreditResponse toResponse(Credit entity) {
    if (entity == null) {
      return null;
    }

    CreditResponse response = new CreditResponse();
    response.setId(entity.getId());
    response.setCreditNumber(entity.getCreditNumber());
    response.setCustomerId(entity.getCustomerId());
    response.setType(CreditResponse.TypeEnum.valueOf(entity.getType().name()));

    response.setOriginalAmount(entity.getOriginalAmount().doubleValue());
    response.setCurrentBalance(entity.getCurrentBalance().doubleValue());
    response.setMonthlyPayment(entity.getMonthlyPayment().doubleValue());
    response.setTotalInstallments(entity.getTotalInstallments());
    response.setPaidInstallments(entity.getPaidInstallments());
    response.setRemainingInstallments(entity.getRemainingInstallments());

    response.setNextPaymentDueDate(entity.getNextPaymentDueDate());
    response.setFinalDueDate(entity.getFinalDueDate());

    response.setIsOverdue(entity.getIsOverdue());
    response.setOverdueDays(entity.getOverdueDays());
    response.setStatus(CreditResponse.StatusEnum.valueOf(entity.getStatus().name()));
    response.setIsActive(entity.isActive());

    response.setCreatedAt(entity.getCreatedAt() != null ?
      entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
    response.setUpdatedAt(entity.getUpdatedAt() != null ?
      entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);

    log.debug("Mapped to response - credit: {}, progress: {}%",
      entity.getId(), entity.getPaymentProgress());

    return response;
  }

  /**
   * Crea CreditCreateRequest seguro basado en el rol del usuario
   */
  public CreditCreateRequest secureCreateRequest(
    CreditCreateRequest originalRequest,
    String authenticatedCustomerId,
    boolean isAdmin) {

    if (originalRequest == null) {
      return null;
    }

    if (isAdmin) {
      log.debug("Admin request - using original customerId: {}", originalRequest.getCustomerId());
      return originalRequest;
    } else {
      CreditCreateRequest securedRequest = new CreditCreateRequest();
      securedRequest.setCustomerId(authenticatedCustomerId);
      securedRequest.setOriginalAmount(
        Optional.ofNullable(originalRequest.getOriginalAmount()).orElse(5000.0)); // Mínimo por defecto

      log.debug("Customer request - original customerId: {}, secured customerId: {}, amount: {}",
        originalRequest.getCustomerId(), authenticatedCustomerId, securedRequest.getOriginalAmount());

      return securedRequest;
    }
  }

  /**
   * Mapea Credit a OverdueProduct para respuestas de elegibilidad
   */
  public OverdueProduct toOverdueProduct(Credit credit) {
    if (credit == null || !Boolean.TRUE.equals(credit.getIsOverdue())) {
      return null;
    }

    OverdueProduct product = new OverdueProduct();
    product.setProductId(credit.getId());
    product.setProductNumber(credit.getCreditNumber());
    product.setProductType(OverdueProduct.ProductTypeEnum.CREDIT);
    product.setOverdueDays(credit.getOverdueDays());
    product.setOverdueAmount(credit.getMonthlyPayment().doubleValue());

    log.debug("Mapped overdue credit: {} - {} days overdue", credit.getId(), credit.getOverdueDays());

    return product;
  }

  /**
   * Crea ProductEligibilityResponse basado en productos vencidos
   */
  public ProductEligibilityResponse toProductEligibilityResponse(String customerId, List<OverdueProduct> overdueProducts) {
    ProductEligibilityResponse response = new ProductEligibilityResponse();
    response.setCustomerId(customerId);
    response.setIsEligible(overdueProducts.isEmpty());
    response.setOverdueProducts(overdueProducts);
    response.setCheckedAt(OffsetDateTime.now());

    if (!overdueProducts.isEmpty()) {
      response.setReason("Customer has overdue debt in credit products");
      log.debug("Customer {} not eligible - {} overdue products", customerId, overdueProducts.size());
    } else {
      log.debug("Customer {} is eligible for new products", customerId);
    }

    return response;
  }

  /**
   * Convierte Credit a CreditBalanceResponse (balance detallado)
   */
  public CreditBalanceResponse toCreditBalanceResponse(Credit credit) {
    if (credit == null) {
      return null;
    }

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
    response.setStatus(CreditBalanceResponse.StatusEnum.valueOf(credit.getStatus().name()));
    response.setIsActive(credit.isActive());

    log.debug("Mapped balance response - credit: {}, balance: {}, progress: {}%",
      credit.getId(), credit.getCurrentBalance(), credit.getPaymentProgress());

    return response;
  }

  /**
   * Helper: Genera número de crédito basado en el tipo
   */
  public String generateCreditNumberByType(String baseNumber, CreditType type) {
    String prefix = (type == CreditType.PERSONAL) ? "PER-" : "ENT-";
    return prefix + baseNumber;
  }

  /**
   * Helper: Valida que el monto esté en el rango permitido
   */
  public boolean isValidCreditAmount(BigDecimal amount) {
    return amount != null &&
      amount.compareTo(new BigDecimal("5000")) >= 0 &&
      amount.compareTo(new BigDecimal("100000")) <= 0;
  }
}
