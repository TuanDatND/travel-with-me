package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.request.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.modules.payment.dto.response.TransactionHistoryPageResponse;
import com.coc.sba_treektour.modules.payment.dto.response.ZaloPayPaymentResponse;

public interface TransactionsService {
    ZaloPayPaymentResponse createZaloPayPayment(CreateZaloPayPaymentRequest request);

    ZaloPayPaymentResponse queryZaloPayPayment(Long transactionId);

    ZaloPayPaymentResponse queryOrderPayment(Long orderId);

    ZaloPayPaymentResponse queryParticipantPayment(Long participantId);

    ZaloPayPaymentResponse retryOrderPayment(CreateZaloPayPaymentRequest request);

    ZaloPayPaymentResponse cancelZaloPayPayment(Long transactionId);

    TransactionHistoryPageResponse getTransactionHistory(int page, int size);
}
