package com.coc.sba_treektour.modules.payment.repository;

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
    }

    @Query(value = """
            SELECT order_id AS "orderId",
                   COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'PAYMENT' THEN amount ELSE 0 END), 0) AS "received",
                   COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' AND transaction_type = 'REFUND' THEN amount ELSE 0 END), 0) AS "refunded",
                   COUNT(*) FILTER (WHERE status = 'PENDING' AND transaction_type = 'PAYMENT') AS "pendingPayments"
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
}
