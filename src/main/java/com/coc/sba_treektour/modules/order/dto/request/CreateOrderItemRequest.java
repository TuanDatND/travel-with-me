package com.coc.sba_treektour.modules.order.dto.request;

import com.coc.sba_treektour.modules.order.entity.OrderItem;
import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderItemType;
import com.coc.sba_treektour.modules.order.exception.InvalidOrderException;
import com.coc.sba_treektour.modules.product.entity.Product;
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

    /**
     * Chuyển đổi DTO thành OrderItem entity với giá được lấy trực tiếp từ Product trong database.
     */
    public OrderItem toEntity(Product product) {
        validateRentalDates();
        BigDecimal price = itemType == OrderItemType.SALE ? product.getSalePrice() : product.getRentalPricePerDay();
        if (price == null) {
            throw new InvalidOrderException("Product does not support selected mode");
        }
        return new OrderItem(
                productId,
                itemType,
                quantity,
                price,
                rentalStartDate,
                rentalEndDate,
                OrderItemStatus.PENDING);
    }

    /**
     * Kiểm tra tính hợp lệ của ngày thuê:
     * - Đồ thuê: bắt buộc có start/end date và end date không được trước start date.
     * - Đồ bán: không được chứa start/end date.
     */
    public void validateRentalDates() {
        boolean hasDates = rentalStartDate != null || rentalEndDate != null;
        if (itemType == OrderItemType.RENTAL && (rentalStartDate == null || rentalEndDate == null || rentalEndDate.isBefore(rentalStartDate))) {
            throw new InvalidOrderException("Rental items require valid start and end dates");
        }
        if (itemType == OrderItemType.SALE && hasDates) {
            throw new InvalidOrderException("Sale items must not include rental dates");
        }
    }
}
