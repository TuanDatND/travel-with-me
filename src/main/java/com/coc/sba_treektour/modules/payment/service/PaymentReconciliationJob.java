package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.PENDING;

/** Reconcile even when the customer closes the browser. HTTP calls happen outside DB locks. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {
    private final TransactionRepository repository;
    private final TransactionsServiceImpl payments;
    private long afterId = 0;

    @Scheduled(fixedDelayString = "${payment.reconciliation.delay-ms:60000}", initialDelayString = "${payment.reconciliation.initial-delay-ms:30000}")
    public void reconcile() {
        var ids = repository.findReconciliationIds(PENDING, OffsetDateTime.now().minusSeconds(30),
                afterId, PageRequest.of(0, 50));
        if (ids.isEmpty()) {
            afterId = 0;
            return;
        }
        for (Long id : ids) {
            try {
                payments.reconcilePendingPayment(id);
            } catch (RuntimeException exception) {
                // Keep PENDING on a transport/provider error. Retry on a subsequent pass.
                log.warn("Payment {} reconciliation failed ({})", id, exception.getClass().getSimpleName());
            }
            afterId = id;
        }
        if (ids.size() < 50) afterId = 0;
    }
}
