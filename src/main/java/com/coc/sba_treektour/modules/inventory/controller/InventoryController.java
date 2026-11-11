package com.coc.sba_treektour.modules.inventory.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.inventory.dto.request.AdjustRequest;
import com.coc.sba_treektour.modules.inventory.dto.request.ImportRequest;
import com.coc.sba_treektour.modules.inventory.dto.request.RentalBookingRequest;
import com.coc.sba_treektour.modules.inventory.dto.request.StatusRequest;
import com.coc.sba_treektour.modules.inventory.dto.response.AvailabilityResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.InventoryPageResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.InventoryResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.SaleAvailabilityResponse;
import com.coc.sba_treektour.modules.inventory.enums.InventoryStatus;
import com.coc.sba_treektour.modules.inventory.service.RentalBookingService;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController @RequestMapping("/api/inventory") @RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventory;
    private final RentalBookingService bookings;
    @PostMapping("/rentals")
    @ResponseStatus(HttpStatus.CREATED)
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
    public ApiResponse<InventoryPageResponse<InventoryResponse>> list(@PathVariable Long branchId,
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
