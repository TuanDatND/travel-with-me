package com.coc.sba_treektour.modules.inventory.dto.response;

public record InventoryResponse(Long id, Long branchId, Long productId, String productName,
        int quantityTotal, int quantityAvailable, int quantityRented, String status) {}
