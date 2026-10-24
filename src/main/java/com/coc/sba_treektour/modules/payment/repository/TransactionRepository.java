package com.coc.sba_treektour.modules.payment.repository;

import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
            Long participantId, String method, String type, TransactionStatus status);

    Optional<Transaction> findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeOrderByIdDesc(
            Long participantId, String method, String type);

    boolean existsByParticipant_IdAndTransactionTypeAndStatusIn(Long participantId, String type,
            java.util.Collection<TransactionStatus> statuses);

    @Query("select t.participant.id from Transaction t where t.id = :id")
    Optional<Long> findParticipantIdByTransactionId(Long id);

    @Query("""
            select t.id from Transaction t join t.participant p
            where t.status = com.coc.sba_treektour.modules.payment.enums.TransactionStatus.SUCCEEDED
              and t.paymentMethod = 'ZALOPAY' and t.transactionType = 'PAYMENT'
              and (p.confirmationReference is null or p.confirmationReference <> concat('TRANSACTION:', cast(t.id as string)))
              and not exists (select r.id from SchedulePaymentReview r where r.transactionId = t.id)
              and t.id > :afterId order by t.id
            """)
    java.util.List<Long> findUnconfirmedParticipantPayments(Long afterId, org.springframework.data.domain.Pageable page);

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
    java.util.List<Long> findReconciliationIds(TransactionStatus status, java.time.OffsetDateTime before,
            Long afterId, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(Long id);
}
