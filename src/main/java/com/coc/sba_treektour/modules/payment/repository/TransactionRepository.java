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
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.participant.id = :participantId and t.transactionType = :type and t.status = :status")
    BigDecimal participantTotal(@Param("participantId") Long participantId, @Param("type") String type,
            @Param("status") TransactionStatus status);

    @Query("""
            select t from Transaction t left join t.order o left join o.user u left join t.participant p
            where t.transactionType = 'REFUND' and t.paymentMethod = 'MANUAL'
              and (u.id = :userId or p.userId = :userId)
            order by t.id desc
            """)
    List<Transaction> findMyManualRefunds(@Param("userId") Long userId, Pageable page);

    List<Transaction> findByTransactionTypeAndPaymentMethodAndStatusOrderByIdDesc(
            String type, String method, TransactionStatus status, Pageable page);

    interface OrderBalanceTotals {
        Long getOrderId();
        BigDecimal getReceived();
        BigDecimal getRefunded();
        long getPendingPayments();
        long getPendingRefunds();
    }

    @Query(value = """
            SELECT order_id AS "orderId",
                   COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0) AS "received",
                   COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0) AS "refunded",
                   COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT') AS "pendingPayments",
                   COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'REFUND') AS "pendingRefunds"
            FROM transactions WHERE order_id IN (:orderIds) AND participant_id IS NULL
            GROUP BY order_id
            """, nativeQuery = true)
    List<OrderBalanceTotals> findOrderBalances(
            @Param("orderIds") List<Long> orderIds);

    Optional<Transaction> findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
            Long participantId, String method, String type, TransactionStatus status);

    Optional<Transaction> findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeOrderByIdDesc(
            Long participantId, String method, String type);

    boolean existsByParticipant_IdAndTransactionTypeAndStatusIn(Long participantId, String type,
            Collection<TransactionStatus> statuses);

    @Query("select t.participant.id from Transaction t where t.id = :id")
    Optional<Long> findParticipantIdByTransactionId(Long id);

    @Query("""
            select t.id from Transaction t join t.participant p
            where t.status = :status
              and t.paymentMethod = 'ZALOPAY' and t.transactionType = 'PAYMENT'
              and (p.confirmationReference is null or p.confirmationReference <> concat('TRANSACTION:', cast(t.id as string)))
              and not exists (select r.id from SchedulePaymentReview r where r.transactionId = t.id)
              and t.id > :afterId order by t.id
            """)
    List<Long> findUnconfirmedParticipantPayments(@Param("status") TransactionStatus status, @Param("afterId") Long afterId, Pageable page);

    Optional<Transaction> findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
            Long orderId, String paymentMethod, String transactionType, TransactionStatus status);

    Optional<Transaction> findFirstByOrder_IdAndPaymentMethodAndTransactionTypeOrderByIdDesc(
            Long orderId, String paymentMethod, String transactionType);

    boolean existsByOrder_IdAndTransactionTypeAndStatus(Long orderId, String transactionType,
            TransactionStatus status);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.order.id = :orderId and t.transactionType = :type and t.status = :status")
    BigDecimal totalAmount(Long orderId, String type,
            TransactionStatus status);

    @Query("select t.order.id from Transaction t where t.id = :id")
    Optional<Long> findOrderIdByTransactionId(Long id);

    @Query("select t.id from Transaction t where t.paymentMethod = 'ZALOPAY' and t.transactionType = 'PAYMENT' and t.status = :status and t.createdAt < :before and t.id > :afterId order by t.id")
    List<Long> findReconciliationIds(TransactionStatus status, OffsetDateTime before,
            Long afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(Long id);

    // Shared native SQL fragments: compile-time constants, no dynamic SQL assembly.
    String REPORT_JOINS = """
            FROM transactions t
                         LEFT JOIN orders o ON o.order_id = t.order_id
                         LEFT JOIN event_participants p ON p.participant_id = t.participant_id
                         LEFT JOIN event_schedules s ON s.schedule_id = p.schedule_id
                         LEFT JOIN tour_events e ON e.event_id = s.event_id
                         LEFT JOIN branches b ON b.branch_id = COALESCE(o.branch_id, s.branch_id)
            """;

    String REPORT_SCOPE = """

             AND (CAST(:#{#filter.branchId} AS bigint) IS NULL OR b.branch_id=CAST(:#{#filter.branchId} AS bigint))
             AND (CAST(:#{#filter.tourId} AS bigint) IS NULL OR s.event_id=CAST(:#{#filter.tourId} AS bigint))
             AND (CAST(:#{#filter.scheduleId} AS bigint) IS NULL OR s.schedule_id=CAST(:#{#filter.scheduleId} AS bigint))
             AND (CAST(:#{#filter.paymentMethod} AS text) IS NULL OR btrim(CAST(:#{#filter.paymentMethod} AS text))='' OR t.payment_method=CAST(:#{#filter.paymentMethod} AS text))
             AND (CAST(:#{#filter.sector == null ? null : #filter.sector.name()} AS text) IS NULL OR (CAST(:#{#filter.sector == null ? null : #filter.sector.name()} AS text)='EQUIPMENT' AND t.order_id IS NOT NULL) OR (CAST(:#{#filter.sector == null ? null : #filter.sector.name()} AS text)='TOUR' AND t.participant_id IS NOT NULL))
            """;

    String REPORT_SUCCEEDED = """
             WHERE t.status='SUCCEEDED' AND t.succeeded_at>=(CAST(:#{#filter.from} AS date)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') AND t.succeeded_at<((CAST(:#{#filter.to} AS date)+1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
            """;

    String REPORT_MONEY = """
            COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE 0 END),0),
            COALESCE(SUM(CASE WHEN t.transaction_type='REFUND' THEN t.amount ELSE 0 END),0),
            COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END),0), COUNT(*) FILTER(WHERE t.transaction_type='PAYMENT'), COUNT(*) FILTER(WHERE t.transaction_type='REFUND')
            """;

    String REPORT_AMOUNTS = """
            COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE 0 END),0), COALESCE(SUM(CASE WHEN t.transaction_type='REFUND' THEN t.amount ELSE 0 END),0), COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END),0)
            """;

    String REPORT_BUCKET = """
            CAST(date_trunc(:#{#filter.period.name().toLowerCase()}, t.succeeded_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS date)
            """;

    String REPORT_PRODUCT_SOURCE = """
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
                                WHERE po.paid_at>=(CAST(:#{#filter.from} AS date)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') AND po.paid_at<((CAST(:#{#filter.to} AS date)+1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
             AND (CAST(:#{#filter.branchId} AS bigint) IS NULL OR o.branch_id=CAST(:#{#filter.branchId} AS bigint))
             AND (CAST(:#{#filter.productId} AS bigint) IS NULL OR i.product_id=CAST(:#{#filter.productId} AS bigint)))
            """;

    @Query(value = "SELECT " + REPORT_MONEY + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE, nativeQuery = true)
    MoneyTotals totals(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT " + REPORT_MONEY + REPORT_JOINS + " WHERE t.status='PENDING' AND t.transaction_type='REFUND'" + REPORT_SCOPE, nativeQuery = true)
    MoneyTotals pendingRefunds(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT " + REPORT_MONEY + REPORT_JOINS + " WHERE t.status='SUCCEEDED' AND t.succeeded_at IS NULL" + REPORT_SCOPE, nativeQuery = true)
    MoneyTotals undated(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT " + REPORT_BUCKET + ", " + REPORT_AMOUNTS + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE + " GROUP BY 1 ORDER BY 1", nativeQuery = true)
    List<RevenuePoint> series(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT b.branch_id,b.branch_name," + REPORT_MONEY + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE + " GROUP BY b.branch_id,b.branch_name ORDER BY COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END),0) DESC,b.branch_id", nativeQuery = true)
    List<RevenueBreakdown> branches(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT CAST(CASE WHEN t.order_id IS NOT NULL THEN 1 ELSE 2 END AS bigint), CASE WHEN t.order_id IS NOT NULL THEN 'EQUIPMENT' ELSE 'TOUR' END, " + REPORT_MONEY + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE + " GROUP BY 1,2 ORDER BY 1", nativeQuery = true)
    List<RevenueBreakdown> sectors(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT e.event_id,e.event_name,COUNT(DISTINCT t.participant_id) FILTER(WHERE t.transaction_type='PAYMENT')," + REPORT_AMOUNTS + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE + " AND t.participant_id IS NOT NULL GROUP BY e.event_id,e.event_name ORDER BY COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END),0) DESC,e.event_id", nativeQuery = true)
    List<TourRevenueRow> tours(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT " + REPORT_BUCKET + ", e.event_id,e.event_name," + REPORT_AMOUNTS + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE + " AND t.participant_id IS NOT NULL GROUP BY 1,e.event_id,e.event_name ORDER BY 1,e.event_id", nativeQuery = true)
    List<TourRevenuePoint> tourSeries(@Param("filter") RevenueReportRequest filter);

    @NativeQuery(value = "SELECT s.schedule_id,e.event_id,e.event_name,s.start_datetime,b.branch_id,b.branch_name,COUNT(DISTINCT t.participant_id) FILTER(WHERE t.transaction_type='PAYMENT') tickets," + "COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE 0 END),0) received, COALESCE(SUM(CASE WHEN t.transaction_type='REFUND' THEN t.amount ELSE 0 END),0) refunded, COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END),0) net " + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE + " AND t.participant_id IS NOT NULL GROUP BY s.schedule_id,s.start_datetime,e.event_id,e.event_name,b.branch_id,b.branch_name ORDER BY s.start_datetime,s.schedule_id", sqlResultSetMapping = "ReportSchedule")
    List<ScheduleRevenueRow> schedules(@Param("filter") RevenueReportRequest filter);

    @Query(value = REPORT_PRODUCT_SOURCE + "SELECT product_id,product_name,CAST(COALESCE(SUM(quantity) FILTER(WHERE item_type='SALE'),0) AS bigint),CAST(COALESCE(SUM(quantity) FILTER(WHERE item_type='RENTAL'),0) AS bigint),CAST(COALESCE(SUM(quantity*days) FILTER(WHERE item_type='RENTAL'),0) AS bigint), COUNT(DISTINCT order_id), COALESCE(SUM(item_value) FILTER(WHERE item_type='SALE'),0),COALESCE(SUM(item_value) FILTER(WHERE item_type='RENTAL'),0),SUM(item_value) FROM product_values GROUP BY product_id,product_name ORDER BY SUM(item_value) DESC,product_id", nativeQuery = true)
    List<ProductRevenueRow> products(@Param("filter") RevenueReportRequest filter);

    @Query(value = REPORT_PRODUCT_SOURCE + "SELECT CAST(date_trunc(:#{#filter.period.name().toLowerCase()}, paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS date),product_id,product_name,COALESCE(SUM(item_value) FILTER(WHERE item_type='SALE'),0),COALESCE(SUM(item_value) FILTER(WHERE item_type='RENTAL'),0),SUM(item_value) FROM product_values GROUP BY 1,product_id,product_name ORDER BY 1,product_id", nativeQuery = true)
    List<ProductRevenuePoint> productSeries(@Param("filter") RevenueReportRequest filter);

    @Query(value = "SELECT COUNT(*)" + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE, nativeQuery = true)
    long transactionCount(@Param("filter") RevenueReportRequest filter);

    @NativeQuery(value = "SELECT t.transaction_id,t.order_id,t.participant_id,b.branch_id,b.branch_name,e.event_id,e.event_name,t.transaction_type,t.payment_method,t.amount,t.created_at,t.succeeded_at " + REPORT_JOINS + REPORT_SUCCEEDED + REPORT_SCOPE + " ORDER BY t.succeeded_at DESC,t.transaction_id DESC LIMIT :limit OFFSET :offset", sqlResultSetMapping = "ReportTransaction")
    List<ReportTransactionRow> transactions(@Param("filter") RevenueReportRequest filter, @Param("offset") long offset, @Param("limit") int limit);

}
