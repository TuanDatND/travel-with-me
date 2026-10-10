package com.coc.sba_treektour.payment.dto;

import com.coc.sba_treektour.payment.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionHistoryResponse(
        Long transactionId,
        Long orderId,
        Long participantId,
        String transactionType,
        BigDecimal amount,
        String paymentMethod,
        TransactionStatus status,
        OffsetDateTime createdAt
) {
}
