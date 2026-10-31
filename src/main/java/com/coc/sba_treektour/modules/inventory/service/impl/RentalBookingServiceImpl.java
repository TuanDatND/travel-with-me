package com.coc.sba_treektour.modules.inventory.service.impl;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.enums.OrderItemType;
import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.common.exception.CatalogException;
import com.coc.sba_treektour.modules.inventory.dto.request.RentalBookingRequest;
import com.coc.sba_treektour.modules.inventory.service.RentalBookingService;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderItemRequest;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;
import com.coc.sba_treektour.modules.order.service.OrderService;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service @RequiredArgsConstructor
public class RentalBookingServiceImpl implements RentalBookingService {
    private final ProductRepository products;
    private final InventoryService inventory;
    private final OrderService orders;

    @Transactional
    @Override
    public OrderResponse book(RentalBookingRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user))
            throw new CatalogException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập để đặt thuê");
        if (!user.isEnabled() || !user.isAccountNonLocked())
            throw new CatalogException(HttpStatus.FORBIDDEN, "Tài khoản không hoạt động");
        if (request.quantity() <= 0) throw CatalogException.invalid("Số lượng phải lớn hơn 0");
        // Cùng thứ tự khóa với thao tác kho; các lượt đặt mới được kiểm tra lại sau khóa.
        var product = products.lockById(request.productId())
                .orElseThrow(() -> CatalogException.missing("Không tìm thấy thiết bị"));
        var stock = inventory.availability(request.branchId(), request.productId(), request.startDate(), request.endDate());
        if (request.quantity() > stock.quantityRentable())
            throw CatalogException.conflict("Không đủ thiết bị cho khoảng ngày đã chọn. Vui lòng chọn lại số lượng.");
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        var total = product.getRentalPricePerDay().multiply(BigDecimal.valueOf(days)).multiply(BigDecimal.valueOf(request.quantity()));
        if (total.precision() - total.scale() > 16) throw CatalogException.invalid("Tổng tiền vượt giới hạn cho phép");
        // Giá và người đặt lấy từ backend/JWT. PENDING được truy vấn availability tính giữ chỗ.
        return orders.create(new CreateOrderRequest(request.branchId(), null, OrderStatus.PENDING, total,
                List.of(new CreateOrderItemRequest(product.getId(), OrderItemType.RENTAL, request.quantity(),
                        product.getRentalPricePerDay(), request.startDate(), request.endDate(), OrderItemStatus.PENDING))), user.getEmail());
    }
}
