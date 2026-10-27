package com.coc.sba_treektour.modules.event.dto.response;

import com.coc.sba_treektour.modules.event.enums.EventStatus;

import java.math.BigDecimal;

public record EventReference(Long eventId, EventStatus status, BigDecimal basePrice) {}
