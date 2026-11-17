package com.coc.sba_treektour.modules.order.dto.response;

import com.coc.sba_treektour.modules.order.enums.OrderItemType;
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
        OffsetDateTime customerReceivedAt, boolean canConfirmReceipt, boolean canReturn, boolean refundPending,
        String deliveryType, Long scheduleId, String deliveryLocation, String deliveryNotes,
        String customerName, String customerPhone, String customerAddress, String customerEmail,
        String returnCutoffTime) {

    public OrderResponse(Long id, Long userId, Long branchId, OrderStatus status,
                         BigDecimal totalAmount, OffsetDateTime orderDate, List<OrderItemResponse> items,
                         String paymentStatus, BigDecimal paidAmount, BigDecimal remainingAmount,
                         boolean canApprove, boolean canCancel,
                         OffsetDateTime customerReceivedAt, boolean canConfirmReceipt, boolean canReturn, boolean refundPending) {
        this(id, userId, branchId, status, totalAmount, orderDate, items, paymentStatus, paidAmount, remainingAmount,
                canApprove, canCancel, customerReceivedAt, canConfirmReceipt, canReturn, refundPending,
                "STORE_PICKUP", null, null, null, null, null, null, null, "18:00");
    }

    public OrderResponse(Long id, Long userId, Long branchId, OrderStatus status,
                         BigDecimal totalAmount, OffsetDateTime orderDate, List<OrderItemResponse> items,
                         String paymentStatus, BigDecimal paidAmount, BigDecimal remainingAmount,
                         boolean canApprove, boolean canCancel,
                         OffsetDateTime customerReceivedAt, boolean canConfirmReceipt, boolean canReturn, boolean refundPending,
                         String deliveryType, Long scheduleId, String deliveryLocation, String deliveryNotes) {
        this(id, userId, branchId, status, totalAmount, orderDate, items, paymentStatus, paidAmount, remainingAmount,
                canApprove, canCancel, customerReceivedAt, canConfirmReceipt, canReturn, refundPending,
                deliveryType, scheduleId, deliveryLocation, deliveryNotes, null, null, null, null, "18:00");
    }

    public static OrderResponse from(Order order, OrderPaymentBalance balance) {
        boolean pending = order.getStatus() == OrderStatus.PENDING;
        var user = order.getUser();
        String cutoff = order.getBranch() != null && order.getBranch().getReturnCutoffTime() != null
                ? order.getBranch().getReturnCutoffTime() : "18:00";
        return new OrderResponse(order.getId(), order.getUserId(), order.getBranchId(), order.getStatus(),
                order.getTotalAmount(), order.getOrderDate(), order.getItems().stream().map(OrderItemResponse::from).toList(),
                balance.status(order.getTotalAmount()), balance.net(),
                order.getTotalAmount().subtract(balance.net()).max(BigDecimal.ZERO),
                pending && !balance.refundPending() && balance.paid(order.getTotalAmount()), pending && balance.cancellable(),
                order.getCustomerReceivedAt(),
                order.getStatus() == OrderStatus.IN_PROGRESS && order.getCustomerReceivedAt() == null,
                order.getStatus() == OrderStatus.IN_PROGRESS && order.getCustomerReceivedAt() != null
                        && order.getItems().stream().anyMatch(item -> item.getItemType() == OrderItemType.RENTAL && item.getStatus() != OrderItemStatus.RETURNED && item.getStatus() != OrderItemStatus.CANCELLED), balance.refundPending(),
                order.getDeliveryType() != null ? order.getDeliveryType() : "STORE_PICKUP",
                order.getScheduleId(),
                order.getDeliveryLocation(),
                order.getDeliveryNotes(),
                order.getCustomerName() != null ? order.getCustomerName() : (user != null ? user.getFullName() : null),
                order.getCustomerPhone() != null ? order.getCustomerPhone() : (user != null ? user.getPhone() : null),
                order.getCustomerAddress() != null ? order.getCustomerAddress() : (user != null ? user.getAddress() : null),
                user != null ? user.getEmail() : null,
                cutoff);
    }
}
