package com.coc.sba_treektour.order.service;

import com.coc.sba_treektour.order.dto.*;
import com.coc.sba_treektour.order.entity.*;
import com.coc.sba_treektour.order.repository.OrderRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) { this.orderRepository = orderRepository; }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = new Order(request.userId(), request.branchId(),
                request.orderDate() == null ? OffsetDateTime.now() : request.orderDate(),
                request.status() == null ? OrderStatus.PENDING : request.status(), request.totalAmount());
        order.replaceItems(request.items().stream().map(this::toEntity).toList());
        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) { return toResponse(findEntity(id)); }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll(Long userId) {
        List<Order> orders = userId == null ? orderRepository.findAll() : orderRepository.findByUserIdOrderByOrderDateDesc(userId);
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse update(Long id, CreateOrderRequest request) {
        Order order = findEntity(id);
        order.update(request.userId(), request.branchId(),
                request.orderDate() == null ? order.getOrderDate() : request.orderDate(),
                request.status() == null ? order.getStatus() : request.status(), request.totalAmount());
        order.replaceItems(request.items().stream().map(this::toEntity).toList());
        return toResponse(order);
    }

    @Transactional
    public void delete(Long id) { orderRepository.delete(findEntity(id)); }

    private Order findEntity(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private OrderItem toEntity(CreateOrderItemRequest request) {
        validateRentalDates(request);
        return new OrderItem(request.productId(), request.itemType(), request.quantity(), request.unitPrice(),
                request.rentalStartDate(), request.rentalEndDate(),
                request.status() == null ? OrderItemStatus.PENDING : request.status());
    }

    private void validateRentalDates(CreateOrderItemRequest request) {
        boolean hasDates = request.rentalStartDate() != null || request.rentalEndDate() != null;
        if (request.itemType() == OrderItemType.RENTAL && (!hasDates || request.rentalEndDate().isBefore(request.rentalStartDate()))) {
            throw new InvalidOrderException("Rental items require valid start and end dates");
        }
        if (request.itemType() == OrderItemType.SALE && hasDates) {
            throw new InvalidOrderException("Sale items must not include rental dates");
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getBranchId(), order.getStatus(),
                order.getTotalAmount(), order.getOrderDate(), order.getItems().stream().map(item -> new OrderItemResponse(
                item.getId(), item.getProductId(), item.getItemType(), item.getQuantity(), item.getUnitPrice(),
                item.getRentalStartDate(), item.getRentalEndDate(), item.getStatus())).toList());
    }
}
