package com.coc.sba_treektour.modules.inventory.dto.request;

import com.coc.sba_treektour.modules.inventory.enums.InventoryStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull InventoryStatus status) {}
