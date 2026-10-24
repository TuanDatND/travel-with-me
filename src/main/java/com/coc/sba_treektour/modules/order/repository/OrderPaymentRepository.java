package com.coc.sba_treektour.modules.order.repository;

import com.coc.sba_treektour.modules.order.dto.OrderPaymentBalance;
import com.coc.sba_treektour.modules.order.entity.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only Spring Data repository. Payment writes remain in the Payment module. */
public interface OrderPaymentRepository extends Repository<Order, Long> {

    interface PaymentTotals {
        Long getOrderId();
        BigDecimal getReceived();
        BigDecimal getRefunded();
        long getPendingPayments();

        default OrderPaymentBalance toBalance() {
            return new OrderPaymentBalance(getReceived(), getRefunded(), getPendingPayments());
        }
    }

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0) AS "received",
                   COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0) AS "refunded",
                   COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT') AS "pendingPayments"
            FROM transactions WHERE order_id = :orderId AND participant_id IS NULL
            """, nativeQuery = true)
    PaymentTotals findTotals(@Param("orderId") Long orderId);

    @Query(value = """
            SELECT order_id AS "orderId",
                   COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0) AS "received",
                   COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0) AS "refunded",
                   COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT') AS "pendingPayments"
            FROM transactions WHERE order_id IN (:orderIds) AND participant_id IS NULL
            GROUP BY order_id
            """, nativeQuery = true)
    List<PaymentTotals> findTotalsByOrderIds(@Param("orderIds") List<Long> orderIds);

    default OrderPaymentBalance read(Long orderId) {
        return orderId == null ? OrderPaymentBalance.EMPTY : findTotals(orderId).toBalance();
    }

    default Map<Long, OrderPaymentBalance> readMany(List<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        return findTotalsByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(PaymentTotals::getOrderId, PaymentTotals::toBalance));
    }
}
