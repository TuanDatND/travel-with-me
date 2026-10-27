package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.request.RequestRefund;
import com.coc.sba_treektour.modules.payment.dto.response.TransactionHistoryResponse;
import java.util.List;

public interface RefundService {
    TransactionHistoryResponse request(RequestRefund request);
    TransactionHistoryResponse confirm(Long refundId);
    List<TransactionHistoryResponse> mine(int page, int size);
    List<TransactionHistoryResponse> pending(int page, int size);
}
