package com.coc.sba_treektour.modules.order.service;

import com.coc.sba_treektour.modules.order.entity.Order;

public interface OrderRefundService {
    /** Called by Payment with the managed order locked, after verifying a full refund. */
    void completeRefund(Order order);
}
