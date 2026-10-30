package com.coc.sba_treektour.modules.payment.dto.response;

import java.util.List;

public record ReportTransactionPage(List<ReportTransactionRow> content, int page, int size, long totalElements, long totalPages) {}
