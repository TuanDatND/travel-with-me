package com.coc.sba_treektour.modules.order.dto.respone;

import com.coc.sba_treektour.modules.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id, Long userId, Long branchId, OrderStatus status,
        BigDecimal totalAmount, OffsetDateTime orderDate, List<OrderItemResponse> items,
        String paymentStatus, BigDecimal paidAmount, BigDecimal remainingAmount,
        boolean canApprove, boolean canCancel) {
}
