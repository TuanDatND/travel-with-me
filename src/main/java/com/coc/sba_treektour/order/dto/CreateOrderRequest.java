package com.coc.sba_treektour.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import com.coc.sba_treektour.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotNull Long branchId,
        OffsetDateTime orderDate,
        OrderStatus status,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal totalAmount,
        @NotEmpty List<@Valid CreateOrderItemRequest> items) {
}
