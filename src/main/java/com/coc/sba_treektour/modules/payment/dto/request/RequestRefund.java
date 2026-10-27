package com.coc.sba_treektour.modules.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

public record RequestRefund(@Positive Long orderId, @Positive Long participantId) {
    @JsonIgnore
    @AssertTrue(message = "Provide exactly one of orderId or participantId")
    public boolean isTargetValid() { return (orderId == null) != (participantId == null); }
}
