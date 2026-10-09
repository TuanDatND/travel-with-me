package com.coc.sba_treektour.order.dto;

import com.coc.sba_treektour.order.entity.OrderItemType;
import com.coc.sba_treektour.order.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateOrderItemRequest(
        @NotNull Long productId,
        @NotNull OrderItemType itemType,
        @Positive int quantity,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal unitPrice,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        OrderItemStatus status) {
}
