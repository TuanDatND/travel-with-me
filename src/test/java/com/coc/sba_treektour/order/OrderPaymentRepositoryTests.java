package com.coc.sba_treektour.order;

import com.coc.sba_treektour.modules.order.dto.OrderPaymentBalance;
import com.coc.sba_treektour.modules.order.repository.OrderPaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPaymentRepositoryTests {
    @Test void projectionMapsSingleAndBatchBalances() {
        var repository = mock(OrderPaymentRepository.class, CALLS_REAL_METHODS);
        var totals = new SpelAwareProxyProjectionFactory().createProjection(
                OrderPaymentRepository.PaymentTotals.class, Map.of(
                        "orderId", 9L, "received", new BigDecimal("100000"),
                        "refunded", new BigDecimal("20000"), "pendingPayments", 1L));
        when(repository.findTotals(9L)).thenReturn(totals);
        when(repository.findTotalsByOrderIds(List.of(9L))).thenReturn(List.of(totals));
        assertThat(repository.read(9L).net()).isEqualByComparingTo("80000");
        assertThat(repository.read(9L).cancellable()).isFalse();
        assertThat(repository.readMany(List.of(9L)).get(9L)).isEqualTo(repository.read(9L));
    }

    @Test void unsavedOrdersAndEmptyListsDoNotQueryDatabase() {
        var repository = mock(OrderPaymentRepository.class, CALLS_REAL_METHODS);
        assertThat(repository.read(null)).isEqualTo(OrderPaymentBalance.EMPTY);
        assertThat(repository.readMany(List.of())).isEmpty();
        verify(repository, never()).findTotals(any());
        verify(repository, never()).findTotalsByOrderIds(any());
    }
}
