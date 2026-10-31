package com.coc.sba_treektour.modules.inventory.service;

import com.coc.sba_treektour.modules.inventory.dto.request.AdjustRequest;
import com.coc.sba_treektour.modules.inventory.dto.response.AvailabilityResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.InventoryPageResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.InventoryResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.SaleAvailabilityResponse;
import com.coc.sba_treektour.modules.inventory.enums.InventoryStatus;
import java.time.LocalDate;

public interface InventoryService {
    InventoryPageResponse<InventoryResponse> list(Long branchId, int page, int size);
    InventoryResponse importStock(Long branchId, Long productId, int quantity);
    InventoryResponse adjust(Long branchId, Long productId, AdjustRequest request);
    InventoryResponse status(Long branchId, Long productId, InventoryStatus status);
    SaleAvailabilityResponse saleAvailability(Long branchId, Long productId);
    AvailabilityResponse availability(Long branchId, Long productId, LocalDate start, LocalDate end);
    void fulfillSale(Long branchId, Long productId, int quantity);
    void handOutRental(Long branchId, Long productId, int quantity);
    void receiveRental(Long branchId, Long productId, int quantity);
}
