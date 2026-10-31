package com.coc.sba_treektour.modules.order.dto.response;

import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.payment.dto.response.OrderPaymentBalance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id, Long userId, Long branchId, OrderStatus status,
        BigDecimal totalAmount, OffsetDateTime orderDate, List<OrderItemResponse> items,
        String paymentStatus, BigDecimal paidAmount, BigDecimal remainingAmount,
        boolean canApprove, boolean canCancel,
        OffsetDateTime customerReceivedAt, boolean canConfirmReceipt, boolean canReturn, boolean refundPending) {

    public static OrderResponse from(Order order, OrderPaymentBalance balance) {
        boolean pending = order.getStatus() == OrderStatus.PENDING;
        return new OrderResponse(order.getId(), order.getUserId(), order.getBranchId(), order.getStatus(),
                order.getTotalAmount(), order.getOrderDate(), order.getItems().stream().map(item -> new OrderItemResponse(
                item.getId(), item.getProductId(), item.getItemType(), item.getQuantity(), item.getUnitPrice(),
                item.getRentalStartDate(), item.getRentalEndDate(), item.getStatus())).toList(),
                balance.status(order.getTotalAmount()), balance.net(),
                order.getTotalAmount().subtract(balance.net()).max(BigDecimal.ZERO),
                pending && !balance.refundPending() && balance.paid(order.getTotalAmount()), pending && balance.cancellable(),
                order.getCustomerReceivedAt(),
                order.getStatus() == OrderStatus.IN_PROGRESS && order.getCustomerReceivedAt() == null,
                order.getStatus() == OrderStatus.IN_PROGRESS && order.getCustomerReceivedAt() != null
                        && order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.RENTED), balance.refundPending());
    }
}
