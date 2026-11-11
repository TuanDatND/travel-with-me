package com.coc.sba_treektour.modules.inventory.dto.request;

import jakarta.validation.constraints.Positive;

public record ImportRequest(@Positive int quantity) {}
