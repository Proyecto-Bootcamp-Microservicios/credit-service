package com.bootcamp.ntt.credit_service.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "credits")
public class Credit {

  @Id
  private String id;

  @NotBlank(message = "El número de crédito es obligatorio")
  @Indexed(unique = true)
  @Field("creditNumber")
  private String creditNumber;

  @NotBlank(message = "El ID del cliente es obligatorio")
  @Indexed
  @Field("customerId")
  private String customerId;

  @NotNull(message = "El tipo de crédito es obligatorio")
  @Field("type")
  private CreditType type; //PERSONAL O ENTERPRISE

  @NotNull(message = "El monto original del préstamo es obligatorio")
  @DecimalMin(value = "5000.00", message = "El monto mínimo del préstamo es $5000")
  @DecimalMax(value = "100000.00", message = "El monto máximo del préstamo es $100000")
  @Digits(integer = 12, fraction = 2, message = "El monto debe tener máximo 12 enteros y 2 decimales")
  @Field("originalAmount")
  private BigDecimal originalAmount;

  @NotNull(message = "El balance actual es obligatorio")
  @DecimalMin(value = "0.00", message = "El balance actual no puede ser negativo")
  @Digits(integer = 12, fraction = 2, message = "El balance debe tener máximo 12 enteros y 2 decimales")
  @Field("currentBalance")
  private BigDecimal currentBalance;

  @NotNull(message = "El pago mensual es obligatorio")
  @DecimalMin(value = "0.01", message = "El pago mensual debe ser mayor a 0")
  @Digits(integer = 10, fraction = 2, message = "El pago mensual debe tener máximo 10 enteros y 2 decimales")
  @Field("monthlyPayment")
  private BigDecimal monthlyPayment;

  @NotNull(message = "El total de cuotas es obligatorio")
  @Min(value = 12, message = "El total de cuotas debe ser 12")
  @Max(value = 12, message = "El total de cuotas debe ser 12")
  @Field("totalInstallments")
  private Integer totalInstallments;

  @NotNull(message = "Las cuotas pagadas son obligatorias")
  @Min(value = 0, message = "Las cuotas pagadas no pueden ser negativas")
  @Field("paidInstallments")
  private Integer paidInstallments;

  @NotNull(message = "Las cuotas restantes son obligatorias")
  @Min(value = 0, message = "Las cuotas restantes no pueden ser negativas")
  @Field("remainingInstallments")
  private Integer remainingInstallments;

  @Field("nextPaymentDueDate")
  private LocalDate nextPaymentDueDate;

  @Field("finalDueDate")
  private LocalDate finalDueDate;

  @Field("isOverdue")
  private Boolean isOverdue;

  @Field("overdueDays")
  private Integer overdueDays;

  @NotNull(message = "El estado del crédito es obligatorio")
  @Field("status")
  private CreditStatus status;

  @NotNull(message = "El estado activo del crédito es obligatorio")
  @Field("isActive")
  private boolean isActive;

  @CreatedDate
  @Field("createdAt")
  private Instant createdAt;

  @LastModifiedDate
  @Field("updatedAt")
  private Instant updatedAt;


  /**
   * Calcula la cuota mensual basada en el monto original
   * Regla: Monto original / 12 cuotas (sin intereses para MVP)
   */
  public void calculateMonthlyPayment() {
    if (originalAmount != null && totalInstallments != null) {
      this.monthlyPayment = originalAmount.divide(
        new BigDecimal(totalInstallments),
        2,
        RoundingMode.HALF_UP
      );
    }
  }

  /**
   * Actualiza el estado de morosidad del crédito
   */
  public void updateOverdueStatus() {
    if (nextPaymentDueDate != null) {
      LocalDate today = LocalDate.now();
      this.isOverdue = today.isAfter(nextPaymentDueDate);
      this.overdueDays = this.isOverdue ?
        (int) nextPaymentDueDate.until(today, ChronoUnit.DAYS) : 0;

      // Actualizar status basado en el estado
      if (Boolean.TRUE.equals(isOverdue)) {
        this.status = CreditStatus.OVERDUE;
      } else if (remainingInstallments != null && remainingInstallments == 0) {
        this.status = CreditStatus.PAID;
      } else if (Boolean.TRUE.equals(isActive)) {
        this.status = CreditStatus.ACTIVE;
      }
    }
  }

  /**
   * Procesa un pago de cuota
   * @param paymentAmount Monto del pago
   * @return true si el pago fue exitoso
   */
  public boolean processPayment(BigDecimal paymentAmount) {
    if (paymentAmount.compareTo(monthlyPayment) < 0) {
      return false;
    }

    this.currentBalance = this.currentBalance.subtract(monthlyPayment);

    this.paidInstallments++;

    this.remainingInstallments--;

    if (remainingInstallments > 0) {
      this.nextPaymentDueDate = this.nextPaymentDueDate.plusMonths(1);
    }

    updateOverdueStatus();

    return true;
  }

  /**
   * Calcula el porcentaje de progreso del préstamo
   */
  public BigDecimal getPaymentProgress() {
    if (totalInstallments == null || paidInstallments == null) {
      return BigDecimal.ZERO;
    }

    return new BigDecimal(paidInstallments)
      .multiply(new BigDecimal("100"))
      .divide(new BigDecimal(totalInstallments), 2, RoundingMode.HALF_UP);
  }

  /**
   * Inicializa los valores por defecto para un nuevo préstamo
   */
  public void initializeNewCredit() {
    this.totalInstallments = 12;
    this.paidInstallments = 0;
    this.remainingInstallments = 12;
    this.currentBalance = this.originalAmount;
    this.isOverdue = false;
    this.overdueDays = 0;
    this.status = CreditStatus.ACTIVE;
    this.isActive = true;

    calculateMonthlyPayment();

    this.nextPaymentDueDate = LocalDate.now().plusMonths(1);

    this.finalDueDate = LocalDate.now().plusMonths(12);
  }
}
