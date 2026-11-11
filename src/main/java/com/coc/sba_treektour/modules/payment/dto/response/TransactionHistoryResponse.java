package com.coc.sba_treektour.modules.payment.dto.response;

import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
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
        OffsetDateTime createdAt,
        String refundReason,
        OffsetDateTime succeededAt,
        Long refundConfirmedBy,
        String refundTransferReference,
        String refundConfirmationNote
) {
}
