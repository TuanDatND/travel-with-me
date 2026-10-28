package com.coc.sba_treektour.modules.tour.dto.request;

import com.coc.sba_treektour.modules.tour.enums.EventStatus;

import jakarta.validation.constraints.NotNull;

public record EventStatusInput(@NotNull EventStatus status) {}
