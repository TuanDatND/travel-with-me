package com.coc.sba_treektour.modules.schedule.entity;

import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule_participant_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipantHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "participant_id", nullable = false)
    private EventParticipant participant;
    @Column(name = "registration_reference", nullable = false) private UUID registrationReference;
    @Column(nullable = false, length = 50) private String action;
    @Column(name = "old_status", length = 30) private String oldStatus;
    @Column(name = "new_status", nullable = false, length = 30) private String newStatus;
    @Column(name = "registered_price", nullable = false, precision = 18, scale = 2) private BigDecimal registeredPrice;
    @Column(name = "changed_by") private Long changedBy;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(name = "changed_at", nullable = false) private OffsetDateTime changedAt;

    public ParticipantHistory(EventParticipant participant, String action, ParticipantStatus oldStatus,
            Long actor, String reason, OffsetDateTime now) {
        this.participant = participant;
        this.registrationReference = participant.getRegistrationReference();
        this.action = action;
        this.oldStatus = oldStatus == null ? null : oldStatus.name();
        this.newStatus = participant.getParticipantStatus().name();
        this.registeredPrice = participant.getRegisteredPrice();
        this.changedBy = actor;
        this.reason = reason;
        this.changedAt = now;
    }
}
