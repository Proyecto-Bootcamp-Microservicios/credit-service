package com.bootcamp.ntt.credit_service.mapper;

import com.bootcamp.ntt.credit_service.entity.Credit;
import com.bootcamp.ntt.credit_service.entity.CreditType;
import com.bootcamp.ntt.credit_service.model.CreditRequest;
import com.bootcamp.ntt.credit_service.model.CreditResponse;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class CreditMapper {

  public Credit toEntity(CreditRequest dto,String customerType) {
    if (dto == null) {
      return null;
    }

    Credit credit = new Credit();
    credit.setCreditNumber(dto.getCreditNumber());
    credit.setCustomerId(dto.getCustomerId());
    credit.setType(CreditType.valueOf(customerType));
    credit.setCreditLimit(BigDecimal.valueOf(dto.getCreditLimit()));
    credit.setAvailableCredit(
      dto.getAvailableCredit() != null ? BigDecimal.valueOf(dto.getAvailableCredit()) : BigDecimal.ZERO
    );
    credit.setActive(dto.getIsActive() != null ? dto.getIsActive() : true);
    return credit;
  }


  public Credit updateEntity(Credit existing, CreditRequest dto) {
    if (existing == null || dto == null) {
      return existing;
    }

    if (dto.getCreditNumber() != null) {
      existing.setCreditNumber(dto.getCreditNumber());
    }

    if (dto.getCustomerId() != null) {
      existing.setCustomerId(dto.getCustomerId());
    }

    if (dto.getCreditLimit() != null) {
      existing.setCreditLimit(BigDecimal.valueOf(dto.getCreditLimit()));
    }

    if (dto.getAvailableCredit() != null) {
      existing.setAvailableCredit(BigDecimal.valueOf(dto.getAvailableCredit()));
    }

    if (dto.getIsActive() != null) {
      existing.setActive(dto.getIsActive());
    }
    return existing;
  }

  public CreditResponse toResponse(Credit entity) {
    if (entity == null) {
      return null;
    }

    CreditResponse response = new CreditResponse();
    response.setId(entity.getId());
    response.setCreditNumber(entity.getCreditNumber());
    response.setCustomerId(entity.getCustomerId());
    response.setType(CreditResponse.TypeEnum.valueOf(entity.getType().name()));
    response.setCreditLimit(entity.getCreditLimit().doubleValue());
    response.setAvailableCredit(entity.getAvailableCredit().doubleValue());
    response.setIsActive(entity.isActive());

    response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
    response.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);

    return response;
  }

}
