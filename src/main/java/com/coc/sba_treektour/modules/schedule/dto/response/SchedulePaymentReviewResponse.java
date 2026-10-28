package com.coc.sba_treektour.modules.schedule.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SchedulePaymentReviewResponse(Long id, Long participantId, UUID registrationReference,
        Long transactionId, String kind, BigDecimal amount, String reason, OffsetDateTime createdAt) {}
