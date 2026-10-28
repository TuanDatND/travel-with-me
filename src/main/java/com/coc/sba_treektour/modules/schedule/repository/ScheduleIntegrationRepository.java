package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data integration queries anchored to the Schedule aggregate; no external entity copies or CRUD. */
public interface ScheduleIntegrationRepository extends Repository<EventSchedule, Long> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM branches WHERE branch_id=:branchId AND status='ACTIVE')", nativeQuery = true)
    boolean branchActive(@Param("branchId") Long branchId);

    @Query(value = """
            SELECT EXISTS(SELECT 1 FROM staff s JOIN branches b ON b.branch_id=s.branch_id
            WHERE s.user_id=:userId AND s.branch_id=:branchId AND s.status='ACTIVE' AND b.status='ACTIVE')
            """, nativeQuery = true)
    boolean staffAssigned(@Param("userId") Long userId, @Param("branchId") Long branchId);

    /** Evaluate the PostgreSQL lock function in FROM, returning a scalar Spring Data can map. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended('tour-guide:' || CAST(:userId AS text), 0))", nativeQuery = true)
    int lockGuide(@Param("userId") Long userId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM transactions WHERE participant_id=:participantId)", nativeQuery = true)
    boolean hasTransactions(@Param("participantId") Long participantId);

    @Query(value = """
            SELECT transaction_id AS "id", amount AS "amount", status AS "status", created_at AS "createdAt"
            FROM transactions WHERE participant_id=:participantId AND transaction_type='PAYMENT'
            ORDER BY transaction_id
            """, nativeQuery = true)
    List<PaymentRecord> payments(@Param("participantId") Long participantId);

    interface PaymentRecord {
        Long getId();
        BigDecimal getAmount();
        String getStatus();
        Instant getCreatedAt();
    }
}
