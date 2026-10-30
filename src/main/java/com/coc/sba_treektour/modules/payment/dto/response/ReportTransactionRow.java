package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReportTransactionRow(Long transactionId, Long orderId, Long participantId, Long branchId, String branchName, Long tourId, String tourName, String transactionType, String paymentMethod, BigDecimal amount, OffsetDateTime createdAt, OffsetDateTime succeededAt) {}
