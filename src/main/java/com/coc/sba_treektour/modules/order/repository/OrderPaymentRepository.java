package com.coc.sba_treektour.modules.order.repository;

import com.coc.sba_treektour.modules.order.dto.OrderPaymentBalance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

/** Order-owned read queries only; payment writes remain in the Payment module. */
@Repository
@RequiredArgsConstructor
public class OrderPaymentRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;


    public OrderPaymentBalance findBalance(Long orderId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0),
                       COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT')
                FROM transactions WHERE order_id = ? AND participant_id IS NULL
                """, (rs, row) -> new OrderPaymentBalance(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getLong(3)), orderId);
    }

    public Map<Long, OrderPaymentBalance> findBalances(List<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        var balances = new HashMap<Long, OrderPaymentBalance>();
        namedJdbc.query("""
                SELECT order_id,
                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0),
                       COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT')
                FROM transactions WHERE order_id IN (:ids) AND participant_id IS NULL
                GROUP BY order_id
                """, Map.of("ids", orderIds), (RowCallbackHandler) rs ->
                balances.put(rs.getLong(1), new OrderPaymentBalance(rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getLong(4))));
        return balances;
    }
}
