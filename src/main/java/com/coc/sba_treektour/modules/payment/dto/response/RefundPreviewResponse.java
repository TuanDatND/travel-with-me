package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;

public record RefundPreviewResponse(Long orderId, Long participantId, BigDecimal receivedAmount,
        BigDecimal refundedAmount, BigDecimal refundableAmount) {}
