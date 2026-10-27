package com.coc.sba_treektour.modules.payment.dto.response;

import java.util.List;

public record TransactionHistoryPageResponse(
        List<TransactionHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
