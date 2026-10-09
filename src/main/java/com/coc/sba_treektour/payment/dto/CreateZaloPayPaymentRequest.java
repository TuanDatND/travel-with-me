package com.coc.sba_treektour.payment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateZaloPayPaymentRequest(
        Long orderId,
        Long participantId,
        @NotNull @DecimalMin("1") @Digits(integer = 18, fraction = 0) BigDecimal amount,
        @NotBlank @Size(max = 50) String appUser,
        @Size(max = 256) String description,
        @Size(max = 2048) String redirectUrl
) {
    @AssertTrue(message = "Provide exactly one of orderId or participantId")
    public boolean isTargetValid() {
        return (orderId == null) != (participantId == null);
    }
}
