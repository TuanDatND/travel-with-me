package com.coc.sba_treektour.modules.order.dto.request;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateOrderRequest(
        @NotNull @Positive Long branchId,
        OffsetDateTime orderDate,
        OrderStatus status,
        BigDecimal totalAmount,
        @NotEmpty List<@NotNull @Valid CreateOrderItemRequest> items,
        String deliveryType,
        Long scheduleId,
        String deliveryLocation,
        String deliveryNotes) {

    public CreateOrderRequest(Long branchId, OffsetDateTime orderDate, OrderStatus status, BigDecimal totalAmount, List<CreateOrderItemRequest> items) {
        this(branchId, orderDate, status, totalAmount, items, "STORE_PICKUP", null, null, null);
    }

    /**
     * Khởi tạo đối tượng Order entity với trạng thái PENDING và thông tin nhận hàng.
     */
    public Order toEntity(User user, Branch branch) {
        Order order = new Order(user, branch, OffsetDateTime.now(), OrderStatus.PENDING, BigDecimal.ZERO);
        order.setDeliveryInfo(deliveryType, scheduleId, deliveryLocation, deliveryNotes);
        return order;
    }
}
