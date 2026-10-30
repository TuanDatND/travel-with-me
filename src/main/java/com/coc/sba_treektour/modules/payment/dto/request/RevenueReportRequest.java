package com.coc.sba_treektour.modules.payment.dto.request;

import com.coc.sba_treektour.modules.payment.enums.ReportPeriod;
import com.coc.sba_treektour.modules.payment.enums.RevenueSector;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@Getter
@Setter
public class RevenueReportRequest {
    @NotNull @DateTimeFormat(iso = DATE)
    private LocalDate from;
    @NotNull @DateTimeFormat(iso = DATE)
    private LocalDate to;
    @Positive private Long branchId;
    @Positive private Long productId;
    @Positive private Long tourId;
    @Positive private Long scheduleId;
    @Size(max = 50) private String paymentMethod;
    @NotNull private ReportPeriod period = ReportPeriod.DAY;
    private RevenueSector sector;

    @AssertTrue(message = "Khoảng ngày không hợp lệ hoặc vượt quá 366 ngày")
    public boolean isDateRangeValid() {
        return from == null || to == null || (!to.isBefore(from) && !to.isAfter(from.plusDays(365)));
    }

    @AssertTrue(message = "Không kết hợp bộ lọc sản phẩm với tour/lịch khởi hành")
    public boolean isTargetValid() {
        return !(productId != null && (tourId != null || scheduleId != null || sector == RevenueSector.TOUR))
                && !((tourId != null || scheduleId != null) && sector == RevenueSector.EQUIPMENT);
    }
}
