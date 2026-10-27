package com.coc.sba_treektour.modules.payment.service;



/** Internal reconciliation entry point; no client-supplied payment outcomes. */
public interface PaymentReconciliationService {
    void reconcilePendingPayment(Long transactionId);
}
