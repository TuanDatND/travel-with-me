package com.coc.sba_treektour.modules.tour.dto.request;

import com.coc.sba_treektour.modules.tour.enums.GuideStatus;

import jakarta.validation.constraints.NotNull;

public record GuideStatusInput(@NotNull GuideStatus status) {}
