package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.request.RequestRefund;
import com.coc.sba_treektour.modules.payment.dto.request.ConfirmRefundRequest;
import com.coc.sba_treektour.modules.payment.dto.response.TransactionHistoryResponse;
import java.util.List;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import com.coc.sba_treektour.modules.payment.dto.response.RefundPreviewResponse;

public interface RefundService {
    RefundPreviewResponse preview(Long orderId, Long participantId);
    TransactionHistoryResponse request(RequestRefund request);
    TransactionHistoryResponse confirm(Long refundId, ConfirmRefundRequest request);
    List<TransactionHistoryResponse> mine(int page, int size);
    List<TransactionHistoryResponse> list(TransactionStatus status, int page, int size);
}
