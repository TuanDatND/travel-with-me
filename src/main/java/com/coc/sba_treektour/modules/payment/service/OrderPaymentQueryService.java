package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.OrderPaymentBalance;
import java.util.List;
import java.util.Map;

/** Internal database reads only: does not query ZaloPay or change an order. */
public interface OrderPaymentQueryService {
    OrderPaymentBalance read(Long orderId);
    Map<Long, OrderPaymentBalance> readMany(List<Long> orderIds);
}
