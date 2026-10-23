package com.coc.sba_treektour.modules.schedule.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable requests for Payment reconciliation; never represents a completed refund. */
@Entity
@Table(name = "schedule_payment_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulePaymentReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "participant_id", nullable = false)
    private EventParticipant participant;
    @Column(name = "registration_reference", nullable = false) private UUID registrationReference;
    @Column(name = "transaction_id") private Long transactionId;
    @Column(name = "dedup_key", nullable = false, unique = true, length = 200) private String dedupKey;
    @Column(nullable = false, length = 50) private String kind;
    @Column(nullable = false, precision = 18, scale = 2) private java.math.BigDecimal amount;
    @Column(nullable = false, columnDefinition = "TEXT") private String reason;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    public SchedulePaymentReview(EventParticipant participant, UUID reference, Long transactionId,
            String key, String kind, java.math.BigDecimal amount, String reason, OffsetDateTime now) {
        this.participant = participant;
        this.registrationReference = reference;
        this.transactionId = transactionId;
        this.dedupKey = key;
        this.kind = kind;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = now;
    }
}
