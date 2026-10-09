package com.coc.sba_treektour.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ZaloPayCallbackRequest(
        @NotBlank String data,
        @NotBlank String mac,
        @NotNull Integer type
) {
}
