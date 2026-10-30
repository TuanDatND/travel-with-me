package com.coc.sba_treektour.modules.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmRefundRequest(
        @NotBlank(message = "Nhập mã tham chiếu chuyển tiền")
        @Size(max = 200) String transferReference,
        @Size(max = 2000) String note) {
}
