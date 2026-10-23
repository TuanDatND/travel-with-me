package com.coc.sba_treektour.modules.schedule.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "event_participants",
    uniqueConstraints = @UniqueConstraint(name = "uq_event_participants_schedule_user", columnNames = {"schedule_id", "user_id"})
)
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

    protected EventParticipant() {}

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

    public Long getId() { return id; }
    public EventSchedule getSchedule() { return schedule; }
    public Long getUserId() { return userId; }
    public OffsetDateTime getRegistrationDate() { return registrationDate; }
    public ParticipantStatus getParticipantStatus() { return participantStatus; }
    public CheckInStatus getCheckInStatus() { return checkInStatus; }
    public BigDecimal getRegisteredPrice() { return registeredPrice; }
    public String getNote() { return note; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public UUID getRegistrationReference() { return registrationReference; }
    public String getConfirmationReference() { return confirmationReference; }

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
