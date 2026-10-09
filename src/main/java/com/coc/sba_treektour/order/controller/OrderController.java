package com.coc.sba_treektour.order.controller;

import com.coc.sba_treektour.order.dto.CreateOrderRequest;
import com.coc.sba_treektour.order.dto.OrderResponse;
import com.coc.sba_treektour.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) { this.orderService = orderService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) { return orderService.create(request); }

    @GetMapping
    public List<OrderResponse> findAll(@RequestParam(required = false) Long userId) { return orderService.findAll(userId); }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) { return orderService.findById(id); }

    @PutMapping("/{id}")
    public OrderResponse update(@PathVariable Long id, @Valid @RequestBody CreateOrderRequest request) { return orderService.update(id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { orderService.delete(id); }
}
