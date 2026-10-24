package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.PENDING;

class PaymentReconciliationTests {
    @Test void retriesOnNextPassAndContinuesAfterOneProviderFailure() {
        var repository = mock(TransactionRepository.class);
        var payments = mock(TransactionsServiceImpl.class);
        var job = new PaymentReconciliationJob(repository,payments);
        when(repository.findReconciliationIds(eq(PENDING),any(OffsetDateTime.class),eq(0L),any()))
                .thenReturn(List.of(1L,2L));
        doThrow(new RuntimeException("network")).when(payments).reconcilePendingPayment(1L);
        job.reconcile();
        job.reconcile();
        verify(payments,times(2)).reconcilePendingPayment(1L);
        verify(payments,times(2)).reconcilePendingPayment(2L);
    }

    @Test void confirmationRetryRunsEvenWithoutPendingProviderPayments() {
        var repository = mock(TransactionRepository.class);
        var payments = mock(TransactionsServiceImpl.class);
        var job = new PaymentReconciliationJob(repository,payments);
        when(repository.findUnconfirmedParticipantPayments(eq(0L),any())).thenReturn(List.of(11L));
        when(repository.findReconciliationIds(any(),any(),anyLong(),any())).thenReturn(List.of());
        job.reconcile();
        verify(payments).reconcilePendingPayment(11L);
    }

    @Test void emptyBatchDoesNotCallProvider() {
        var repository = mock(TransactionRepository.class);
        var payments = mock(TransactionsServiceImpl.class);
        var job = new PaymentReconciliationJob(repository,payments);
        when(repository.findReconciliationIds(any(),any(),anyLong(),any())).thenReturn(List.of());
        job.reconcile();
        verifyNoInteractions(payments);
    }
}
