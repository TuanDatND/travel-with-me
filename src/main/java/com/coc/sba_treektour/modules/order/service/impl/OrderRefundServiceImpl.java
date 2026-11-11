package com.coc.sba_treektour.modules.order.service.impl;

import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.exception.InvalidOrderException;
import com.coc.sba_treektour.modules.order.service.OrderRefundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderRefundServiceImpl implements OrderRefundService {
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeRefund(Order order) {
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CANCELLED) {
            throw new InvalidOrderException("Only undelivered pending or cancelled orders can be refunded");
        }
        order.setStatus(OrderStatus.CANCELLED);
        // Nothing was handed over: release rental reservations by status, never add stock back.
        order.getItems().forEach(item -> item.setStatus(OrderItemStatus.CANCELLED));
    }
}
