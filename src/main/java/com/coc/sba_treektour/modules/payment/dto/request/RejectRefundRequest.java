package com.coc.sba_treektour.modules.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRefundRequest(
        @NotBlank(message = "Please enter a rejection reason")
        @Size(max = 2000) String reason) {
}
