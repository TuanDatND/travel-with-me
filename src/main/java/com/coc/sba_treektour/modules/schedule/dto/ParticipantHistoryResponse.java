package com.coc.sba_treektour.modules.schedule.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
public record ParticipantHistoryResponse(Long id, UUID registrationReference, String action,
        String oldStatus, String newStatus, java.math.BigDecimal registeredPrice, Long changedBy, String reason, OffsetDateTime changedAt) {}
