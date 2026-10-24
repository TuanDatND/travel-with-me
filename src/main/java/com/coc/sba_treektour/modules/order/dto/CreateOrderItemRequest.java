package com.coc.sba_treektour.modules.order.dto;

import com.coc.sba_treektour.modules.order.entity.OrderItemType;
import com.coc.sba_treektour.modules.order.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateOrderItemRequest(
        @NotNull @Positive Long productId,
        @NotNull OrderItemType itemType,
        @Positive int quantity,
        BigDecimal unitPrice,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        OrderItemStatus status) {
}
