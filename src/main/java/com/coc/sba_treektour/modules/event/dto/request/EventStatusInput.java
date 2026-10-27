package com.coc.sba_treektour.modules.event.dto.request;

import com.coc.sba_treektour.modules.event.enums.EventStatus;

import jakarta.validation.constraints.NotNull;

public record EventStatusInput(@NotNull EventStatus status) {}
