package com.coc.sba_treektour.schedule.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_schedule_history")
public class EventScheduleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private EventSchedule schedule;

    @Column(name = "old_start_datetime")
    private OffsetDateTime oldStartDatetime;

    @Column(name = "new_start_datetime")
    private OffsetDateTime newStartDatetime;

    @Column(name = "old_end_datetime")
    private OffsetDateTime oldEndDatetime;

    @Column(name = "new_end_datetime")
    private OffsetDateTime newEndDatetime;

    @Column(name = "old_status", length = 30)
    private String oldStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    protected EventScheduleHistory() {}

    public EventScheduleHistory(EventSchedule schedule,
                                OffsetDateTime oldStartDatetime, OffsetDateTime newStartDatetime,
                                OffsetDateTime oldEndDatetime, OffsetDateTime newEndDatetime,
                                String oldStatus, String newStatus,
                                String changeReason) {
        this.schedule = schedule;
        this.oldStartDatetime = oldStartDatetime;
        this.newStartDatetime = newStartDatetime;
        this.oldEndDatetime = oldEndDatetime;
        this.newEndDatetime = newEndDatetime;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changeReason = changeReason;
        this.changedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public EventSchedule getSchedule() { return schedule; }
    public OffsetDateTime getOldStartDatetime() { return oldStartDatetime; }
    public OffsetDateTime getNewStartDatetime() { return newStartDatetime; }
    public OffsetDateTime getOldEndDatetime() { return oldEndDatetime; }
    public OffsetDateTime getNewEndDatetime() { return newEndDatetime; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public String getChangeReason() { return changeReason; }
    public OffsetDateTime getChangedAt() { return changedAt; }
}
