package com.coc.sba_treektour.modules.payment.dto;

import java.math.BigDecimal;

/**
 * Read-only snapshot of transaction totals for an equipment order.
 */
public record OrderPaymentBalance(BigDecimal received, BigDecimal refunded, long pendingPayments) {
    public static final OrderPaymentBalance EMPTY = new OrderPaymentBalance(BigDecimal.ZERO, BigDecimal.ZERO, 0);

    public BigDecimal net() {
        return received.subtract(refunded).max(BigDecimal.ZERO);
    }

    public boolean paid(BigDecimal total) {
        return net().compareTo(total) >= 0;
    }

    public boolean cancellable() {
        return received.signum() == 0 && pendingPayments == 0;
    }

    public String status(BigDecimal total) {
        if (paid(total)) return "PAID";
        if (received.signum() > 0 && net().signum() == 0) return "REFUNDED";
        return net().signum() > 0 ? "PARTIALLY_PAID" : "UNPAID";
    }
}
