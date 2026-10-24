package com.coc.sba_treektour.modules.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import tools.jackson.databind.JsonNode;

public record ZaloPayPaymentResponse(
        @JsonProperty("transaction_id") Long transactionId,
        @JsonProperty("app_trans_id") String appTransId,
        TransactionStatus status,
        Long orderId,
        Long participantId,
        String participantConfirmation,
        String redirectUrl,
        @JsonProperty("zalopay_response") JsonNode zaloPayResponse
) {}
