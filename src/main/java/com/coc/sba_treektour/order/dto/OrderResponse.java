package com.coc.sba_treektour.order.dto;

import com.coc.sba_treektour.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id, Long userId, Long branchId, OrderStatus status,
        BigDecimal totalAmount, OffsetDateTime orderDate, List<OrderItemResponse> items) {
}
