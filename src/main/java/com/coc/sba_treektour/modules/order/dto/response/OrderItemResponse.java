package com.coc.sba_treektour.modules.order.dto.response;

import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderItemType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderItemResponse(
        Long id, Long productId, OrderItemType itemType, int quantity,
        BigDecimal unitPrice, LocalDate rentalStartDate, LocalDate rentalEndDate,
        OrderItemStatus status) {
}
