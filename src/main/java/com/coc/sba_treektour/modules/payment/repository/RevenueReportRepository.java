package com.coc.sba_treektour.modules.payment.repository;

import com.coc.sba_treektour.modules.payment.dto.request.RevenueReportRequest;
import com.coc.sba_treektour.modules.payment.dto.response.MoneyTotals;
import com.coc.sba_treektour.modules.payment.dto.response.ProductRevenuePoint;
import com.coc.sba_treektour.modules.payment.dto.response.ProductRevenueRow;
import com.coc.sba_treektour.modules.payment.dto.response.ReportTransactionRow;
import com.coc.sba_treektour.modules.payment.dto.response.RevenueBreakdown;
import com.coc.sba_treektour.modules.payment.dto.response.RevenuePoint;
import com.coc.sba_treektour.modules.payment.dto.response.ScheduleRevenueRow;
import com.coc.sba_treektour.modules.payment.dto.response.TourRevenuePoint;
import com.coc.sba_treektour.modules.payment.dto.response.TourRevenueRow;
import com.coc.sba_treektour.modules.payment.enums.ReportPeriod;
import com.coc.sba_treektour.modules.payment.enums.RevenueSector;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only reporting queries owned by Payment; monetary sums never join order_items. */
@Repository
@RequiredArgsConstructor
public class RevenueReportRepository {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String JOINS = """
             FROM transactions t
             LEFT JOIN orders o ON o.order_id = t.order_id
             LEFT JOIN event_participants p ON p.participant_id = t.participant_id
             LEFT JOIN event_schedules s ON s.schedule_id = p.schedule_id
             LEFT JOIN tour_events e ON e.event_id = s.event_id
             LEFT JOIN branches b ON b.branch_id = COALESCE(o.branch_id, s.branch_id)
            """;
    private static final String MONEY = """
            COALESCE(SUM(CASE WHEN t.transaction_type = 'PAYMENT' THEN t.amount ELSE 0 END),0) received,
            COALESCE(SUM(CASE WHEN t.transaction_type = 'REFUND' THEN t.amount ELSE 0 END),0) refunded,
            COUNT(*) FILTER (WHERE t.transaction_type = 'PAYMENT') payments,
            COUNT(*) FILTER (WHERE t.transaction_type = 'REFUND') refunds
            """;
    private final NamedParameterJdbcTemplate jdbc;

    private MapSqlParameterSource params(RevenueReportRequest f) {
        return new MapSqlParameterSource().addValue("from", f.getFrom().atStartOfDay(ZONE).toOffsetDateTime())
                .addValue("until", f.getTo().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime())
                .addValue("branch", f.getBranchId()).addValue("tour", f.getTourId())
                .addValue("schedule", f.getScheduleId()).addValue("method", f.getPaymentMethod())
                .addValue("product", f.getProductId());
    }

    private String scope(RevenueReportRequest f) {
        StringBuilder sql = new StringBuilder();
        if (f.getBranchId() != null) sql.append(" AND b.branch_id = :branch");
        if (f.getTourId() != null) sql.append(" AND s.event_id = :tour");
        if (f.getScheduleId() != null) sql.append(" AND s.schedule_id = :schedule");
        if (f.getPaymentMethod() != null && !f.getPaymentMethod().isBlank()) sql.append(" AND t.payment_method = :method");
        if (f.getSector() == RevenueSector.EQUIPMENT) sql.append(" AND t.order_id IS NOT NULL");
        if (f.getSector() == RevenueSector.TOUR) sql.append(" AND t.participant_id IS NOT NULL");
        return sql.toString();
    }

    private String succeeded(RevenueReportRequest f) {
        return " WHERE t.status = 'SUCCEEDED' AND t.succeeded_at >= :from AND t.succeeded_at < :until" + scope(f);
    }

    private String bucket(RevenueReportRequest f, String column) {
        // Only enum-selected SQL fragments; no user text is interpolated into SQL.
        return "date_trunc('" + (f.getPeriod() == ReportPeriod.MONTH ? "month" : "day")
                + "', " + column + " AT TIME ZONE 'Asia/Ho_Chi_Minh')::date";
    }

    private MoneyTotals money(ResultSet rs) throws SQLException {
        BigDecimal received = rs.getBigDecimal("received"), refunded = rs.getBigDecimal("refunded");
        return new MoneyTotals(received, refunded, received.subtract(refunded), rs.getLong("payments"), rs.getLong("refunds"));
    }

    public MoneyTotals totals(RevenueReportRequest f) {
        return jdbc.queryForObject("SELECT " + MONEY + JOINS + succeeded(f), params(f), (rs, row) -> money(rs));
    }

    /** Pending is a current backlog, independent of the historical success-date window. */
    public MoneyTotals pendingRefunds(RevenueReportRequest f) {
        return jdbc.queryForObject("SELECT " + MONEY + JOINS
                + " WHERE t.status = 'PENDING' AND t.transaction_type = 'REFUND'" + scope(f), params(f), (rs, row) -> money(rs));
    }

    /** Never silently substitute created_at for unknown historical success times. */
    public MoneyTotals undated(RevenueReportRequest f) {
        return jdbc.queryForObject("SELECT " + MONEY + JOINS
                + " WHERE t.status = 'SUCCEEDED' AND t.succeeded_at IS NULL" + scope(f), params(f), (rs, row) -> money(rs));
    }

    public List<RevenuePoint> series(RevenueReportRequest f) {
        return jdbc.query("SELECT " + bucket(f, "t.succeeded_at") + " period, " + MONEY + JOINS + succeeded(f)
                + " GROUP BY 1 ORDER BY 1", params(f), (rs, row) -> {
                    var m = money(rs);
                    return new RevenuePoint(rs.getObject("period", LocalDate.class), m.receivedAmount(), m.refundedAmount(), m.netAmount());
                });
    }

    public List<RevenueBreakdown> branches(RevenueReportRequest f) {
        return jdbc.query("SELECT b.branch_id id, b.branch_name name, " + MONEY + JOINS + succeeded(f)
                + " GROUP BY b.branch_id,b.branch_name ORDER BY SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END) DESC,b.branch_id", params(f), (rs, row) -> breakdown(rs));
    }

    public List<RevenueBreakdown> sectors(RevenueReportRequest f) {
        return jdbc.query("SELECT CASE WHEN t.order_id IS NOT NULL THEN 1 ELSE 2 END::bigint id, "
                + "CASE WHEN t.order_id IS NOT NULL THEN 'EQUIPMENT' ELSE 'TOUR' END name, " + MONEY + JOINS + succeeded(f)
                + " GROUP BY 1,2 ORDER BY 1", params(f), (rs, row) -> breakdown(rs));
    }

    private RevenueBreakdown breakdown(ResultSet rs) throws SQLException {
        var m = money(rs);
        return new RevenueBreakdown(rs.getObject("id", Long.class), rs.getString("name"), m.receivedAmount(),
                m.refundedAmount(), m.netAmount(), m.paymentCount(), m.refundCount());
    }

    public List<TourRevenueRow> tours(RevenueReportRequest f) {
        return jdbc.query("SELECT e.event_id id,e.event_name name, " + MONEY
                + ",COUNT(DISTINCT t.participant_id) FILTER (WHERE t.transaction_type='PAYMENT') tickets" + JOINS + succeeded(f)
                + " AND t.participant_id IS NOT NULL GROUP BY e.event_id,e.event_name ORDER BY SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END) DESC,e.event_id",
                params(f), (rs, row) -> {
                    var m = money(rs);
                    return new TourRevenueRow(rs.getLong("id"), rs.getString("name"), rs.getLong("tickets"),
                            m.receivedAmount(), m.refundedAmount(), m.netAmount());
                });
    }

    public List<TourRevenuePoint> tourSeries(RevenueReportRequest f) {
        return jdbc.query("SELECT " + bucket(f, "t.succeeded_at") + " period,e.event_id id,e.event_name name, " + MONEY
                + JOINS + succeeded(f) + " AND t.participant_id IS NOT NULL GROUP BY 1,e.event_id,e.event_name ORDER BY 1,e.event_id",
                params(f), (rs, row) -> {
                    var m = money(rs);
                    return new TourRevenuePoint(rs.getObject("period", LocalDate.class), rs.getLong("id"), rs.getString("name"),
                            m.receivedAmount(), m.refundedAmount(), m.netAmount());
                });
    }

    public List<ScheduleRevenueRow> schedules(RevenueReportRequest f) {
        return jdbc.query("SELECT s.schedule_id,s.start_datetime,e.event_id,e.event_name,b.branch_id,b.branch_name," + MONEY
                + ",COUNT(DISTINCT t.participant_id) FILTER (WHERE t.transaction_type='PAYMENT') tickets" + JOINS + succeeded(f)
                + " AND t.participant_id IS NOT NULL GROUP BY s.schedule_id,s.start_datetime,e.event_id,e.event_name,b.branch_id,b.branch_name ORDER BY s.start_datetime,s.schedule_id",
                params(f), (rs, row) -> {
                    var m = money(rs);
                    return new ScheduleRevenueRow(rs.getLong("schedule_id"), rs.getLong("event_id"), rs.getString("event_name"),
                            rs.getObject("start_datetime", OffsetDateTime.class), rs.getLong("branch_id"), rs.getString("branch_name"),
                            rs.getLong("tickets"), m.receivedAmount(), m.refundedAmount(), m.netAmount());
                });
    }

    private String productSource(RevenueReportRequest f) {
        // Attribute each item once, on the first time gross successful payments fully covered the order.
        // Legacy undated payments make that time unknown; exclude those orders instead of guessing.
        String sql = """
                WITH payment_progress AS (
                    SELECT t.order_id,t.succeeded_at,t.transaction_id,o.total_amount,
                           SUM(t.amount) OVER (PARTITION BY t.order_id ORDER BY t.succeeded_at,t.transaction_id) received
                    FROM transactions t JOIN orders o ON o.order_id=t.order_id
                    WHERE t.transaction_type='PAYMENT' AND t.status='SUCCEEDED'
                      AND t.succeeded_at IS NOT NULL AND o.total_amount>0
                      AND NOT EXISTS (SELECT 1 FROM transactions old WHERE old.order_id=t.order_id
                          AND old.transaction_type='PAYMENT' AND old.status='SUCCEEDED' AND old.succeeded_at IS NULL)
                ), paid_orders AS (
                    SELECT order_id,MIN(succeeded_at) paid_at FROM payment_progress
                    WHERE received>=total_amount GROUP BY order_id
                ), product_values AS (
                    SELECT i.product_id,pr.product_name,i.item_type,i.quantity,o.order_id,po.paid_at,
                           CASE WHEN i.item_type='RENTAL' THEN i.rental_end_date-i.rental_start_date+1 ELSE 1 END days,
                           i.unit_price*i.quantity*(CASE WHEN i.item_type='RENTAL'
                               THEN i.rental_end_date-i.rental_start_date+1 ELSE 1 END) item_value
                    FROM paid_orders po JOIN orders o ON o.order_id=po.order_id
                    JOIN order_items i ON i.order_id=o.order_id JOIN products pr ON pr.product_id=i.product_id
                    WHERE po.paid_at>=:from AND po.paid_at<:until
                """;
        if (f.getBranchId() != null) sql += " AND o.branch_id=:branch";
        if (f.getProductId() != null) sql += " AND i.product_id=:product";
        return sql + ") ";
    }

    public List<ProductRevenueRow> products(RevenueReportRequest f) {
        return jdbc.query(productSource(f) + """
                SELECT product_id,product_name,
                COALESCE(SUM(quantity) FILTER(WHERE item_type='SALE'),0) sale_quantity,
                COALESCE(SUM(quantity) FILTER(WHERE item_type='RENTAL'),0) rental_quantity,
                COALESCE(SUM(quantity*days) FILTER(WHERE item_type='RENTAL'),0) rental_days,
                COUNT(DISTINCT order_id) orders,
                COALESCE(SUM(item_value) FILTER(WHERE item_type='SALE'),0) sale_value,
                COALESCE(SUM(item_value) FILTER(WHERE item_type='RENTAL'),0) rental_value
                FROM product_values GROUP BY product_id,product_name ORDER BY SUM(item_value) DESC,product_id
                """, params(f), (rs, row) -> {
                    BigDecimal sale = rs.getBigDecimal("sale_value"), rental = rs.getBigDecimal("rental_value");
                    return new ProductRevenueRow(rs.getLong("product_id"), rs.getString("product_name"), rs.getLong("sale_quantity"),
                            rs.getLong("rental_quantity"), rs.getLong("rental_days"), rs.getLong("orders"), sale, rental, sale.add(rental));
                });
    }

    public List<ProductRevenuePoint> productSeries(RevenueReportRequest f) {
        return jdbc.query(productSource(f) + "SELECT " + bucket(f, "paid_at") + " period, product_id,product_name,"
                + "COALESCE(SUM(item_value) FILTER(WHERE item_type='SALE'),0) sale_value,"
                + "COALESCE(SUM(item_value) FILTER(WHERE item_type='RENTAL'),0) rental_value"
                + " FROM product_values GROUP BY 1,product_id,product_name ORDER BY 1,product_id", params(f), (rs, row) -> {
                    BigDecimal sale = rs.getBigDecimal("sale_value"), rental = rs.getBigDecimal("rental_value");
                    return new ProductRevenuePoint(rs.getObject("period", LocalDate.class), rs.getLong("product_id"),
                            rs.getString("product_name"), sale, rental, sale.add(rental));
                });
    }

    public long transactionCount(RevenueReportRequest f) {
        return jdbc.queryForObject("SELECT COUNT(*)" + JOINS + succeeded(f), params(f), Long.class);
    }

    public List<ReportTransactionRow> transactions(RevenueReportRequest f, int page, int size) {
        return jdbc.query("""
                SELECT t.transaction_id,t.order_id,t.participant_id,b.branch_id,b.branch_name,e.event_id,e.event_name,
                       t.transaction_type,t.payment_method,t.amount,t.created_at,t.succeeded_at
                """ + JOINS + succeeded(f) + " ORDER BY t.succeeded_at DESC,t.transaction_id DESC LIMIT :limit OFFSET :offset",
                params(f).addValue("limit", size).addValue("offset", (long) page * size), (rs, row) -> new ReportTransactionRow(
                        rs.getLong("transaction_id"), rs.getObject("order_id", Long.class), rs.getObject("participant_id", Long.class),
                        rs.getObject("branch_id", Long.class), rs.getString("branch_name"), rs.getObject("event_id", Long.class),
                        rs.getString("event_name"), rs.getString("transaction_type"), rs.getString("payment_method"), rs.getBigDecimal("amount"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("succeeded_at", OffsetDateTime.class)));
    }
}
