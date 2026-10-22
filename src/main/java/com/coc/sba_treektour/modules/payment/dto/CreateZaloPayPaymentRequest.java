package com.coc.sba_treektour.modules.payment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateZaloPayPaymentRequest(
        Long orderId,
        Long participantId,
        @DecimalMin("1") @Digits(integer = 18, fraction = 0) BigDecimal amount,
        @Size(max = 50) String appUser,
        @Size(max = 256) String description,
        @Size(max = 2048) String redirectUrl
) {
    @AssertTrue(message = "Tour payment requires amount and appUser until Participant integration")
    public boolean isParticipantPaymentValid() {
        return participantId == null || (amount != null && appUser != null && !appUser.isBlank());
    }

    @AssertTrue(message = "Provide exactly one of orderId or participantId")
    public boolean isTargetValid() {
        return (orderId == null) != (participantId == null);
    }
}
