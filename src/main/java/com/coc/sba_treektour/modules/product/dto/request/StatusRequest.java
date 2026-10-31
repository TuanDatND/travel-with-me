package com.coc.sba_treektour.modules.product.dto.request;

import com.coc.sba_treektour.modules.product.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull ProductStatus status) {}
