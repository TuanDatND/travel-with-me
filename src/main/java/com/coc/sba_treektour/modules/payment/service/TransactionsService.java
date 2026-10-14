package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.modules.payment.dto.ZaloPayCallbackRequest;
import com.coc.sba_treektour.modules.payment.dto.ZaloPayCallbackResponse;
import com.coc.sba_treektour.modules.payment.dto.ZaloPayPaymentResponse;
import com.coc.sba_treektour.modules.payment.dto.TransactionHistoryResponse;

import java.util.List;

public interface TransactionsService {
    ZaloPayPaymentResponse createZaloPayPayment(CreateZaloPayPaymentRequest request);

    ZaloPayPaymentResponse queryZaloPayPayment(Long transactionId);

    ZaloPayCallbackResponse processZaloPayCallback(ZaloPayCallbackRequest request);

    List<TransactionHistoryResponse> getTransactionHistory();
}
