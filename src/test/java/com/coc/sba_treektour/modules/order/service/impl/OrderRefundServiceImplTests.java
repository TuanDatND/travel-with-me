package com.coc.sba_treektour.modules.order.service.impl;

import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.entity.OrderItem;
import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.exception.InvalidOrderException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderRefundServiceImplTests {
    private final OrderRefundServiceImpl service = new OrderRefundServiceImpl();

    @Test void fullRefundCancelsOrderAndReleasesReservationsByItemStatus() {
        Order order = mock(Order.class);
        OrderItem sale = mock(OrderItem.class);
        OrderItem rental = mock(OrderItem.class);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getItems()).thenReturn(List.of(sale, rental));

        service.completeRefund(order);

        verify(order).setStatus(OrderStatus.CANCELLED);
        verify(sale).setStatus(OrderItemStatus.CANCELLED);
        verify(rental).setStatus(OrderItemStatus.CANCELLED);
    }

    @Test void alreadyCancelledOrderIsSupportedButApprovedOrDeliveredOrdersAreRejected() {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(order.getItems()).thenReturn(List.of());
        service.completeRefund(order);
        verify(order).setStatus(OrderStatus.CANCELLED);

        for (OrderStatus status : List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED)) {
            Order delivered = mock(Order.class);
            when(delivered.getStatus()).thenReturn(status);
            assertThatThrownBy(() -> service.completeRefund(delivered)).isInstanceOf(InvalidOrderException.class);
            verify(delivered, never()).setStatus(OrderStatus.CANCELLED);
        }
    }
}
