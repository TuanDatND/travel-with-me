package com.coc.sba_treektour.modules.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@JsonIgnoreProperties("redirectUrl")
public record CreateZaloPayPaymentRequest(
        @Schema(description = "ID đơn thiết bị; chỉ truyền orderId hoặc participantId", example = "5")
        Long orderId,
        @Schema(description = "ID vé tour; không truyền cùng orderId", example = "7")
        Long participantId,
        @Schema(hidden = true)
        @DecimalMin("1") @Digits(integer = 18, fraction = 0) BigDecimal amount,
        @Schema(hidden = true)
        @Size(max = 50) String appUser,
        @Schema(description = "Nội dung thanh toán tùy chọn", example = "Thanh toán Travel With Me")
        @Size(max = 256) String description
) {
    @JsonIgnore
    @AssertTrue(message = "Provide exactly one of orderId or participantId")
    public boolean isTargetValid() {
        return (orderId == null) != (participantId == null);
    }
}
