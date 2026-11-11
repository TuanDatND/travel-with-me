package com.coc.sba_treektour.modules.inventory.dto.response;

public record SaleAvailabilityResponse(Long branchId, Long productId, int quantityAvailable) {}
