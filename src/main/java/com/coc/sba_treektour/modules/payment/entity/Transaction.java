package com.coc.sba_treektour.modules.payment.entity;

import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.payment.dto.response.ReportTransactionRow;
import com.coc.sba_treektour.modules.payment.dto.response.ScheduleRevenueRow;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import jakarta.persistence.Column;
import jakarta.persistence.ColumnResult;
import jakarta.persistence.ConstructorResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.SqlResultSetMappings;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@ToString
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "ReportSchedule", classes = @ConstructorResult(targetClass = ScheduleRevenueRow.class, columns = {
        @ColumnResult(name = "schedule_id", type = Long.class),
        @ColumnResult(name = "event_id", type = Long.class),
        @ColumnResult(name = "event_name", type = String.class),
        @ColumnResult(name = "start_datetime", type = OffsetDateTime.class),
        @ColumnResult(name = "branch_id", type = Long.class),
        @ColumnResult(name = "branch_name", type = String.class),
        @ColumnResult(name = "tickets", type = Long.class),
        @ColumnResult(name = "received", type = BigDecimal.class),
        @ColumnResult(name = "refunded", type = BigDecimal.class),
        @ColumnResult(name = "net", type = BigDecimal.class)
    })),
    @SqlResultSetMapping(name = "ReportTransaction", classes = @ConstructorResult(targetClass = ReportTransactionRow.class, columns = {
        @ColumnResult(name = "transaction_id", type = Long.class),
        @ColumnResult(name = "order_id", type = Long.class),
        @ColumnResult(name = "participant_id", type = Long.class),
        @ColumnResult(name = "branch_id", type = Long.class),
        @ColumnResult(name = "branch_name", type = String.class),
        @ColumnResult(name = "event_id", type = Long.class),
        @ColumnResult(name = "event_name", type = String.class),
        @ColumnResult(name = "transaction_type", type = String.class),
        @ColumnResult(name = "payment_method", type = String.class),
        @ColumnResult(name = "amount", type = BigDecimal.class),
        @ColumnResult(name = "created_at", type = OffsetDateTime.class),
        @ColumnResult(name = "succeeded_at", type = OffsetDateTime.class)
    }))
})
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @ToString.Exclude
    private Order order;

    // DTO/API vẫn công bố ID Long, không trả entity Order ra JSON.
    public Long getOrderId() {
        return order == null ? null : order.getId();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    @ToString.Exclude
    private EventParticipant participant;

    public Long getParticipantId() { return participant == null ? null : participant.getId(); }

    @Column(name = "succeeded_at")
    private OffsetDateTime succeededAt;

    @Column(name = "refund_reason", length = 2000)
    private String refundReason;

    @Column(name = "refund_confirmed_by")
    private Long refundConfirmedBy;

    @Column(name = "refund_transfer_reference", length = 200)
    private String refundTransferReference;

    @Column(name = "refund_confirmation_note", length = 2000)
    private String refundConfirmationNote;

    @Column(name = "refund_rejected_by")
    private Long refundRejectedBy;

    @Column(name = "refund_rejected_at")
    private OffsetDateTime refundRejectedAt;

    @Column(name = "refund_rejection_reason", length = 2000)
    private String refundRejectionReason;

    @Size(max = 30)
    @NotNull
    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Size(max = 50)
    @NotNull
    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
