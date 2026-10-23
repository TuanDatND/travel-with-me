package com.coc.sba_treektour.modules.schedule.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adapter đọc contract ngoài module; không sửa bảng của Account/Branch/Payment. */
@Repository
public class ScheduleIntegrationRepository {
    private final JdbcTemplate jdbc;
    public ScheduleIntegrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean branchActive(Long branchId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM branches WHERE branch_id=? AND status='ACTIVE')", Boolean.class, branchId));
    }

    public boolean staffAssigned(Long userId, Long branchId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM staff s JOIN branches b ON b.branch_id=s.branch_id
                WHERE s.user_id=? AND s.branch_id=? AND s.status='ACTIVE' AND b.status='ACTIVE')
                """, Boolean.class, userId, branchId));
    }

    public void lockGuide(Long userId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('tour-guide:' || CAST(? AS text), 0))", userId);
    }

    public boolean hasTransactions(Long participantId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM transactions WHERE participant_id=?)", Boolean.class, participantId));
    }

    public List<PaymentRecord> payments(Long participantId) {
        return jdbc.query("""
                SELECT transaction_id,amount,status,created_at FROM transactions
                WHERE participant_id=? AND transaction_type='PAYMENT' ORDER BY transaction_id
                """, (rs, row) -> new PaymentRecord(rs.getLong(1), rs.getBigDecimal(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class)), participantId);
    }

    public record PaymentRecord(Long id, BigDecimal amount, String status, OffsetDateTime createdAt) {}
}
