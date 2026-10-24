package com.coc.sba_treektour.modules.inventory.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.inventory.dto.InventoryDtos.*;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;
import com.coc.sba_treektour.modules.product.dto.ProductDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/inventory") @RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventory;
    private final com.coc.sba_treektour.modules.inventory.service.RentalBookingService bookings;
    @PostMapping("/rentals")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<OrderResponse> book(@Valid @RequestBody RentalBookingRequest request) {
        return ApiResponse.success(bookings.book(request));
    }
    @GetMapping("/availability")
    public ApiResponse<AvailabilityResponse> availability(@RequestParam Long branchId, @RequestParam Long productId,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.success(inventory.availability(branchId, productId, startDate, endDate));
    }
    @GetMapping("/sale-availability")
    public ApiResponse<SaleAvailabilityResponse> saleAvailability(@RequestParam Long branchId, @RequestParam Long productId) {
        return ApiResponse.success(inventory.saleAvailability(branchId, productId));
    }
    @GetMapping("/branches/{branchId}")
    public ApiResponse<PageResponse<InventoryResponse>> list(@PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inventory.list(branchId, page, size));
    }
    @PostMapping("/branches/{branchId}/products/{productId}/import")
    public ApiResponse<InventoryResponse> importStock(@PathVariable Long branchId, @PathVariable Long productId,
            @Valid @RequestBody ImportRequest request) {
        return ApiResponse.success(inventory.importStock(branchId, productId, request.quantity()));
    }
    @PutMapping("/branches/{branchId}/products/{productId}")
    public ApiResponse<InventoryResponse> adjust(@PathVariable Long branchId, @PathVariable Long productId,
            @Valid @RequestBody AdjustRequest request) {
        return ApiResponse.success(inventory.adjust(branchId, productId, request));
    }
    @PatchMapping("/branches/{branchId}/products/{productId}/status")
    public ApiResponse<InventoryResponse> status(@PathVariable Long branchId, @PathVariable Long productId,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(inventory.status(branchId, productId, request.status()));
    }
}
