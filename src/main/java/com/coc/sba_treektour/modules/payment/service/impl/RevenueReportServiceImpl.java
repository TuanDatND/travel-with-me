package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.payment.dto.request.RevenueReportRequest;
import com.coc.sba_treektour.modules.payment.dto.response.ProductReportResponse;
import com.coc.sba_treektour.modules.payment.dto.response.ReportTransactionPage;
import com.coc.sba_treektour.modules.payment.dto.response.RevenueOverviewResponse;
import com.coc.sba_treektour.modules.payment.dto.response.TourReportResponse;
import com.coc.sba_treektour.modules.payment.dto.response.RevenuePoint;
import com.coc.sba_treektour.modules.payment.enums.ReportPeriod;
import com.coc.sba_treektour.modules.payment.enums.RevenueSector;
import com.coc.sba_treektour.modules.payment.repository.RevenueReportRepository;
import com.coc.sba_treektour.modules.payment.service.RevenueReportService;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RevenueReportServiceImpl implements RevenueReportService {
    private final RevenueReportRepository reports;
    private final UserRepository users;

    private void requireAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || users.findByEmail(auth.getName())
                .filter(user -> "ACTIVE".equals(user.getStatus()) && user.getAuthorities().stream()
                        .anyMatch(role -> "ADMIN".equals(role.getAuthority()))).isEmpty())
            throw new ResponseStatusException(FORBIDDEN, "Active ADMIN account required");
    }

    private void moneyFilters(RevenueReportRequest request) {
        if (request.getProductId() != null) throw new ResponseStatusException(BAD_REQUEST,
                "productId is only supported by the product-value report, not cash reports");
    }

    @Override
    public RevenueOverviewResponse overview(@Valid RevenueReportRequest request) {
        requireAdmin(); moneyFilters(request);
        var totals = reports.totals(request);
        var pending = reports.pendingRefunds(request);
        var undated = reports.undated(request);
        Map<LocalDate, RevenuePoint> found = reports.series(request).stream().collect(Collectors.toMap(RevenuePoint::date, Function.identity()));
        List<RevenuePoint> series = new ArrayList<>();
        LocalDate cursor = request.getPeriod() == ReportPeriod.MONTH ? request.getFrom().withDayOfMonth(1) : request.getFrom();
        while (!cursor.isAfter(request.getTo())) {
            series.add(found.getOrDefault(cursor, new RevenuePoint(cursor, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
            cursor = request.getPeriod() == ReportPeriod.MONTH ? cursor.plusMonths(1) : cursor.plusDays(1);
        }
        return new RevenueOverviewResponse(request.getFrom(), request.getTo(), "Asia/Ho_Chi_Minh", "BACKEND_VERIFIED_SUCCESS_AT",
                totals, pending.refundedAmount(), pending.refundCount(), undated.paymentCount() + undated.refundCount(),
                undated.receivedAmount(), undated.refundedAmount(), series, reports.branches(request), reports.sectors(request));
    }

    @Override
    public ProductReportResponse products(@Valid RevenueReportRequest request) {
        requireAdmin();
        if (request.getTourId() != null || request.getScheduleId() != null || request.getSector() == RevenueSector.TOUR
                || (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank()))
            throw new ResponseStatusException(BAD_REQUEST, "Product report supports dates, period, branchId and productId; it is not a payment-method allocation");
        return new ProductReportResponse("ITEM_VALUE_IN_FULLY_PAID_ORDERS_BEFORE_REFUNDS", "FIRST_FULL_GROSS_PAYMENT_AT",
                reports.products(request), reports.productSeries(request));
    }

    @Override
    public TourReportResponse tours(@Valid RevenueReportRequest request) {
        requireAdmin(); moneyFilters(request);
        if (request.getSector() == RevenueSector.EQUIPMENT) throw new ResponseStatusException(BAD_REQUEST, "Tour report cannot filter EQUIPMENT");
        return new TourReportResponse(reports.tours(request), reports.tourSeries(request), reports.schedules(request));
    }

    @Override
    public ReportTransactionPage transactions(@Valid RevenueReportRequest request, int page, int size) {
        requireAdmin(); moneyFilters(request);
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(BAD_REQUEST, "page >= 0; size 1-100");
        long count = reports.transactionCount(request);
        return new ReportTransactionPage(reports.transactions(request, page, size), page, size, count, (count + size - 1) / size);
    }

    @Override
    public String csv(@Valid RevenueReportRequest request) {
        requireAdmin(); moneyFilters(request);
        long count = reports.transactionCount(request);
        if (count > 10000) throw new ResponseStatusException(BAD_REQUEST, "CSV export is limited to 10000 transactions; narrow filters");
        StringBuilder csv = new StringBuilder("\ufefftransactionId,orderId,participantId,branch,tour,type,method,amountVnd,createdAt,succeededAt\r\n");
        for (var row : reports.transactions(request, 0, 10000)) {
            csv.append(String.join(",", cell(row.transactionId()), cell(row.orderId()), cell(row.participantId()),
                    cell(row.branchName()), cell(row.tourName()), cell(row.transactionType()), cell(row.paymentMethod()),
                    cell(row.amount()), cell(row.createdAt()), cell(row.succeededAt()))).append("\r\n");
        }
        return csv.toString();
    }

    private String cell(Object value) {
        String text = value == null ? "" : value.toString();
        // Spreadsheet formulas in external names must not execute when the CSV is opened.
        if (text.stripLeading().matches("^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
