package com.coc.sba_treektour.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ZaloPayCallbackResponse(
        @JsonProperty("return_code") int returnCode,
        @JsonProperty("return_message") String returnMessage
) {
}
