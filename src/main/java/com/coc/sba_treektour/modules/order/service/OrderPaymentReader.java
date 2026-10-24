package com.coc.sba_treektour.modules.order.service;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only payment projection owned by Order. Never calls a provider or writes transactions. */
@Component
public class OrderPaymentReader {
    private final JdbcTemplate jdbc;

    public OrderPaymentReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public java.util.Map<Long, Balance> readMany(java.util.List<Long> orderIds) {
        if (orderIds.isEmpty()) return java.util.Map.of();
        var balances = new java.util.HashMap<Long, Balance>();
        new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
                SELECT order_id,
                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0),
                       COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT')
                FROM transactions WHERE order_id IN (:ids) AND participant_id IS NULL
                GROUP BY order_id
                """, java.util.Map.of("ids", orderIds), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                    balances.put(rs.getLong(1), new Balance(rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getLong(4))));
        return balances;
    }

    public record Balance(BigDecimal received, BigDecimal refunded, long pendingPayments) {
        public BigDecimal net() { return received.subtract(refunded).max(BigDecimal.ZERO); }
        public boolean paid(BigDecimal total) { return net().compareTo(total) >= 0; }
        public boolean cancellable() { return received.signum() == 0 && pendingPayments == 0; }
        public String status(BigDecimal total) {
            if (paid(total)) return "PAID";
            if (received.signum() > 0 && net().signum() == 0) return "REFUNDED";
            return net().signum() > 0 ? "PARTIALLY_PAID" : "UNPAID";
        }
    }

    public Balance read(Long orderId) {
        if (orderId == null) return new Balance(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0),
                       COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT')
                FROM transactions WHERE order_id = ? AND participant_id IS NULL
                """, (rs, row) -> new Balance(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getLong(3)), orderId);
    }
}
