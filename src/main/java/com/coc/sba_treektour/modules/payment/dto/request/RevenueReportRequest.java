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

    @AssertTrue(message = "Invalid report range: daily up to 366 days, monthly up to 60 months, yearly up to 10 years")
    public boolean isDateRangeValid() {
        if (from == null || to == null || period == null) return true;
        if (to.isBefore(from)) return false;
        LocalDate lastDay = switch (period) {
            case DAY -> from.plusDays(365);
            case MONTH -> from.plusMonths(60).minusDays(1);
            case YEAR -> from.plusYears(10).minusDays(1);
        };
        return !to.isAfter(lastDay);
    }

    @AssertTrue(message = "Không kết hợp bộ lọc sản phẩm với tour/lịch khởi hành")
    public boolean isTargetValid() {
        return !(productId != null && (tourId != null || scheduleId != null || sector == RevenueSector.TOUR))
                && !((tourId != null || scheduleId != null) && sector == RevenueSector.EQUIPMENT);
    }
}
