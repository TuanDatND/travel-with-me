package com.coc.sba_treektour.payment.service;

import com.coc.sba_treektour.payment.dto.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.payment.dto.ZaloPayCallbackRequest;
import com.coc.sba_treektour.payment.dto.ZaloPayCallbackResponse;
import com.coc.sba_treektour.payment.dto.ZaloPayPaymentResponse;

public interface TransactionsService {
    ZaloPayPaymentResponse createZaloPayPayment(CreateZaloPayPaymentRequest request);

    ZaloPayPaymentResponse queryZaloPayPayment(Long transactionId);

    ZaloPayCallbackResponse processZaloPayCallback(ZaloPayCallbackRequest request);
}
