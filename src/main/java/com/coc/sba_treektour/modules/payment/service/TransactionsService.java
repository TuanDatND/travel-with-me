package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.modules.payment.dto.ZaloPayPaymentResponse;
import com.coc.sba_treektour.modules.payment.dto.TransactionHistoryPageResponse;


public interface TransactionsService {
    ZaloPayPaymentResponse createZaloPayPayment(CreateZaloPayPaymentRequest request);

    ZaloPayPaymentResponse queryZaloPayPayment(Long transactionId);

    TransactionHistoryPageResponse getTransactionHistory(int page, int size);
}
