package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.payment.dto.response.OrderPaymentBalance;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.OrderPaymentQueryService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderPaymentQueryServiceImpl implements OrderPaymentQueryService {
    private final TransactionRepository transactions;

    @Override
    public OrderPaymentBalance read(Long orderId) {
        return orderId == null ? OrderPaymentBalance.EMPTY
                : readMany(List.of(orderId)).getOrDefault(orderId, OrderPaymentBalance.EMPTY);
    }

    @Override
    public Map<Long, OrderPaymentBalance> readMany(List<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        return transactions.findOrderBalances(orderIds).stream().collect(Collectors.toMap(
                TransactionRepository.OrderBalanceTotals::getOrderId,
                totals -> new OrderPaymentBalance(totals.getReceived(), totals.getRefunded(), totals.getPendingPayments())));
    }
}
