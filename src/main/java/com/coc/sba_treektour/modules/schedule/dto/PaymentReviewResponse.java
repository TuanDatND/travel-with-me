package com.coc.sba_treektour.modules.schedule.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
public record PaymentReviewResponse(Long id, Long participantId, UUID registrationReference,
        Long transactionId, String kind, java.math.BigDecimal amount, String reason, OffsetDateTime createdAt) {}
