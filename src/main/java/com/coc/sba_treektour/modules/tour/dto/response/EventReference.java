package com.coc.sba_treektour.modules.tour.dto.response;

import com.coc.sba_treektour.modules.tour.enums.EventStatus;

import java.math.BigDecimal;

public record EventReference(Long eventId, EventStatus status, BigDecimal basePrice) {}
