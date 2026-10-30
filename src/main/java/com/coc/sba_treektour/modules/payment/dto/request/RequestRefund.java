package com.coc.sba_treektour.modules.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestRefund(@Positive Long orderId, @Positive Long participantId,
        @NotBlank(message = "Vui lòng nhập lý do hoàn tiền")
        @Size(max = 2000, message = "Lý do tối đa 2000 ký tự") String reason) {
    @JsonIgnore
    @AssertTrue(message = "Provide exactly one of orderId or participantId")
    public boolean isTargetValid() { return (orderId == null) != (participantId == null); }
}
