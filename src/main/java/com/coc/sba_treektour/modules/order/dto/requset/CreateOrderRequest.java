package com.coc.sba_treektour.modules.order.dto.requset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.coc.sba_treektour.modules.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateOrderRequest(
        @NotNull @jakarta.validation.constraints.Positive Long branchId,
        OffsetDateTime orderDate,
        OrderStatus status,
        BigDecimal totalAmount,
        @NotEmpty List<@NotNull @Valid CreateOrderItemRequest> items) {
}
