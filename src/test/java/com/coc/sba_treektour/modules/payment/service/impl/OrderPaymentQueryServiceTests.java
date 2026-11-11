package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.payment.dto.response.OrderPaymentBalance;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.impl.OrderPaymentQueryServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPaymentQueryServiceTests {
    final TransactionRepository transactions = mock(TransactionRepository.class);
    final OrderPaymentQueryServiceImpl service = new OrderPaymentQueryServiceImpl(transactions);

    @Test void projectionMapsSingleAndBatchBalances() {
        var totals = new SpelAwareProxyProjectionFactory().createProjection(
                TransactionRepository.OrderBalanceTotals.class, Map.of(
                        "orderId", 9L, "received", new BigDecimal("100000"),
                        "refunded", new BigDecimal("20000"), "pendingPayments", 1L, "pendingRefunds", 1L));
        when(transactions.findOrderBalances(List.of(9L))).thenReturn(List.of(totals));
        assertThat(service.read(9L).net()).isEqualByComparingTo("80000");
        assertThat(service.read(9L).cancellable()).isFalse();
        assertThat(service.read(9L).refundPending()).isTrue();
        assertThat(service.readMany(List.of(9L)).get(9L)).isEqualTo(service.read(9L));
    }

    @Test void unsavedOrdersAndEmptyListsDoNotQueryDatabase() {
        assertThat(service.read(null)).isEqualTo(OrderPaymentBalance.EMPTY);
        assertThat(service.readMany(List.of())).isEmpty();
        verifyNoInteractions(transactions);
    }

    @Test void noTransactionsMeansUnpaid() {
        when(transactions.findOrderBalances(List.of(9L))).thenReturn(List.of());
        assertThat(service.read(9L)).isEqualTo(OrderPaymentBalance.EMPTY);
        assertThat(service.read(9L).paid(BigDecimal.ONE)).isFalse();
    }
}
