package com.coc.sba_treektour.modules.order.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.order.dto.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.dto.OrderResponse;
import com.coc.sba_treektour.modules.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;


    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(Principal principal, @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Order created successfully", orderService.create(request, principal.getName())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<OrderResponse>> findAll(@RequestParam(required = false) Long userId) {
        return ApiResponse.success(orderService.findAll(userId));
    }

    @GetMapping("/me")
    public ApiResponse<List<OrderResponse>> findMine(Principal principal) {
        return ApiResponse.success(orderService.findMyOrders(principal.getName()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> findById(@PathVariable Long id, Principal principal) {
        return ApiResponse.success(orderService.findMyOrder(id, principal.getName()));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrderResponse> update(@PathVariable Long id, Principal principal, @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success("Order updated successfully", orderService.update(id, request, principal.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        orderService.delete(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id, Principal principal) {
        return ApiResponse.success(orderService.cancel(id, principal.getName()));
    }

    @PostMapping("/{id}/{action:confirm|hand-over|return}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<OrderResponse> operate(@PathVariable Long id, @PathVariable String action) {
        return ApiResponse.success(orderService.operate(id, action));
    }
}
