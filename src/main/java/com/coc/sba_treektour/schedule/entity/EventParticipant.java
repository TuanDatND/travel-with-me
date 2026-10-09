package com.coc.sba_treektour.schedule.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

    protected EventParticipant() {}

    public EventParticipant(EventSchedule schedule, Long userId, BigDecimal registeredPrice, String note) {
        this.schedule = schedule;
        this.userId = userId;
        this.registeredPrice = registeredPrice;
        this.note = note;
        this.registrationDate = OffsetDateTime.now();
        this.participantStatus = ParticipantStatus.PENDING;
        this.checkInStatus = CheckInStatus.NOT_CHECKED_IN;
    }

    public void reRegister(BigDecimal newPrice, String newNote) {
        this.registeredPrice = newPrice;
        this.note = newNote;
        this.registrationDate = OffsetDateTime.now();
        this.participantStatus = ParticipantStatus.PENDING;
        this.checkInStatus = CheckInStatus.NOT_CHECKED_IN;
    }

    public void confirmPayment() {
        this.participantStatus = ParticipantStatus.CONFIRMED;
    }

    public void cancel() {
        this.participantStatus = ParticipantStatus.CANCELLED;
    }

    public void checkIn() {
        this.checkInStatus = CheckInStatus.CHECKED_IN;
    }

    public void markNoShow() {
        this.participantStatus = ParticipantStatus.NO_SHOW;
    }

    public void complete() {
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
}
