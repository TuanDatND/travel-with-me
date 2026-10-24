package com.coc.sba_treektour.modules.order.service;

import com.coc.sba_treektour.modules.order.dto.OrderPaymentBalance;
import com.coc.sba_treektour.modules.order.repository.OrderPaymentRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/** Read-only adapter; database queries belong to OrderPaymentRepository. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderPaymentReader {
    private final OrderPaymentRepository repository;


    public OrderPaymentBalance read(Long orderId) {
        return orderId == null ? OrderPaymentBalance.EMPTY : repository.findBalance(orderId);
    }

    public Map<Long, OrderPaymentBalance> readMany(List<Long> orderIds) {
        return orderIds.isEmpty() ? Map.of() : repository.findBalances(orderIds);
    }
}
