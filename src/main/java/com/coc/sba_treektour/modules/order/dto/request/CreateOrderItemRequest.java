package com.coc.sba_treektour.modules.order.dto.request;

import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderItemType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
