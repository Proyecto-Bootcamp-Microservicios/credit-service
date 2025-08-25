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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "credits")
public class Credit {

  @Id
  private String id;

  @NotBlank(message = "El número de crédito es obligatorio")
  @Pattern(
    regexp = "^(PER|ENT)-\\d{4,6}$",
    message = "El número de crédito debe empezar con PER- o ENT- seguido de 4 a 6 dígitos"
  )  @Indexed(unique = true)
  @Field("creditNumber")
  private String creditNumber;

  @NotBlank(message = "El ID del cliente es obligatorio")
  @Indexed
  @Field("customerId")
  private String customerId;

  @NotNull(message = "El tipo de crédito es obligatorio")
  @Field("type")
  private CreditType type;

  @NotNull(message = "El límite de crédito es obligatorio")
  @DecimalMin(value = "0.01", message = "El límite de crédito debe ser mayor a 0")
  @Digits(integer = 12, fraction = 2, message = "El límite debe tener máximo 12 enteros y 2 decimales")
  @Field("creditLimit")
  private BigDecimal creditLimit;

  @NotNull(message = "El crédito disponible es obligatorio")
  @DecimalMin(value = "0.00", message = "El crédito disponible no puede ser negativo")
  @Field("availableCredit")
  private BigDecimal availableCredit;

  @NotNull(message = "El estado del crédito es obligatorio")
  @Field("isActive")
  private boolean isActive;

  @CreatedDate
  @Field("createdAt")
  private Instant createdAt;

  @LastModifiedDate
  @Field("updatedAt")
  private Instant updatedAt;

}
