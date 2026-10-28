package com.coc.sba_treektour.modules.schedule.entity;

import com.coc.sba_treektour.modules.schedule.enums.CheckInStatus;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "event_participants",
    uniqueConstraints = @UniqueConstraint(name = "uq_event_participants_schedule_user", columnNames = {"schedule_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private EventSchedule schedule;

    // Foreign key to User in Account module kept as Long until READY_TO_MAP
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "registration_date", nullable = false)
    private OffsetDateTime registrationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_status", nullable = false, length = 30)
    private ParticipantStatus participantStatus = ParticipantStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_status", nullable = false, length = 30)
    private CheckInStatus checkInStatus = CheckInStatus.NOT_CHECKED_IN;

    @Column(name = "registered_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal registeredPrice;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "registration_reference", nullable = false)
    private UUID registrationReference;

    @Column(name = "confirmation_reference", length = 200)
    private String confirmationReference;

    public EventParticipant(EventSchedule schedule, Long userId, BigDecimal registeredPrice, String note,
            OffsetDateTime now, OffsetDateTime expiresAt) {
        this.schedule = schedule;
        this.userId = userId;
        this.registeredPrice = registeredPrice;
        this.note = note;
        this.registrationDate = now;
        this.expiresAt = expiresAt;
        this.registrationReference = UUID.randomUUID();
        this.participantStatus = ParticipantStatus.PENDING;
        this.checkInStatus = CheckInStatus.NOT_CHECKED_IN;
    }

    public void reRegister(BigDecimal newPrice, String newNote, OffsetDateTime now, OffsetDateTime expiresAt) {
        if (participantStatus != ParticipantStatus.CANCELLED && participantStatus != ParticipantStatus.EXPIRED)
            throw new IllegalStateException("Chỉ được đăng ký lại vé đã hủy/hết hạn");
        this.registeredPrice = newPrice;
        this.note = newNote;
        this.registrationDate = now;
        this.expiresAt = expiresAt;
        this.registrationReference = UUID.randomUUID();
        this.confirmationReference = null;
        this.participantStatus = ParticipantStatus.PENDING;
        this.checkInStatus = CheckInStatus.NOT_CHECKED_IN;
    }

    public void confirmPayment(String reference, OffsetDateTime now) {
        if (participantStatus != ParticipantStatus.PENDING || !now.isBefore(expiresAt))
            throw new IllegalStateException("Vé không còn trong thời hạn thanh toán");
        this.confirmationReference = reference;
        this.participantStatus = ParticipantStatus.CONFIRMED;
    }

    public void cancel() {
        if (participantStatus != ParticipantStatus.PENDING && participantStatus != ParticipantStatus.CONFIRMED)
            throw new IllegalStateException("Chỉ được hủy vé đang giữ chỗ/đã xác nhận");
        this.participantStatus = ParticipantStatus.CANCELLED;
    }

    public void checkIn() {
        if (participantStatus != ParticipantStatus.CONFIRMED) throw new IllegalStateException("Vé chưa xác nhận");
        this.checkInStatus = CheckInStatus.CHECKED_IN;
    }

    public void markNoShow() {
        if (participantStatus != ParticipantStatus.CONFIRMED || checkInStatus == CheckInStatus.CHECKED_IN)
            throw new IllegalStateException("Không thể đánh dấu vắng mặt");
        this.participantStatus = ParticipantStatus.NO_SHOW;
    }

    public void complete() {
        if (participantStatus != ParticipantStatus.CONFIRMED || checkInStatus != CheckInStatus.CHECKED_IN)
            throw new IllegalStateException("Chỉ hoàn tất vé đã xác nhận và điểm danh");
        this.participantStatus = ParticipantStatus.COMPLETED;
    }

    public boolean occupiesSeat() {
        return participantStatus == ParticipantStatus.PENDING || participantStatus == ParticipantStatus.CONFIRMED;
    }

    public boolean expire(OffsetDateTime now) {
        if (participantStatus != ParticipantStatus.PENDING || now.isBefore(expiresAt)) return false;
        participantStatus = ParticipantStatus.EXPIRED;
        return true;
    }

    public void shortenHold(OffsetDateTime cutoff) {
        if (participantStatus == ParticipantStatus.PENDING && cutoff.isBefore(expiresAt)) expiresAt = cutoff;
    }
}
