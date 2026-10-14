package com.coc.sba_treektour.modules.order.dto;

import com.coc.sba_treektour.modules.order.entity.OrderItemStatus;
import com.coc.sba_treektour.modules.order.entity.OrderItemType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderItemResponse(
        Long id, Long productId, OrderItemType itemType, int quantity,
        BigDecimal unitPrice, LocalDate rentalStartDate, LocalDate rentalEndDate,
        OrderItemStatus status) {
}
