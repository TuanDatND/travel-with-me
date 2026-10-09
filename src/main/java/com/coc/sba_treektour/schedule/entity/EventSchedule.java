package com.coc.sba_treektour.schedule.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_schedules")
public class EventSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    // Foreign keys to other modules kept as Long until their status is READY_TO_MAP
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "guide_id")
    private Long guideId;

    @Column(name = "start_datetime", nullable = false)
    private OffsetDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private OffsetDateTime endDatetime;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    @Column(name = "current_participants", nullable = false)
    private Integer currentParticipants = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ScheduleStatus status = ScheduleStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EventSchedule() {}

    public EventSchedule(Long eventId, Long branchId, Long guideId,
                         OffsetDateTime startDatetime, OffsetDateTime endDatetime,
                         Integer maxParticipants) {
        this.eventId = eventId;
        this.branchId = branchId;
        this.guideId = guideId;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.maxParticipants = maxParticipants;
        this.currentParticipants = 0;
        this.status = ScheduleStatus.OPEN;
        this.createdAt = OffsetDateTime.now();
    }

    public void updateDetails(Long guideId, Integer maxParticipants) {
        this.guideId = guideId;
        this.maxParticipants = maxParticipants;
        if (this.currentParticipants >= maxParticipants && this.status == ScheduleStatus.OPEN) {
            this.status = ScheduleStatus.FULL;
        } else if (this.currentParticipants < maxParticipants && this.status == ScheduleStatus.FULL) {
            this.status = ScheduleStatus.OPEN;
        }
    }

    public void reschedule(OffsetDateTime newStart, OffsetDateTime newEnd) {
        this.startDatetime = newStart;
        this.endDatetime = newEnd;
    }

    public void updateStatus(ScheduleStatus newStatus) {
        this.status = newStatus;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getBranchId() { return branchId; }
    public Long getGuideId() { return guideId; }
    public OffsetDateTime getStartDatetime() { return startDatetime; }
    public OffsetDateTime getEndDatetime() { return endDatetime; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public Integer getCurrentParticipants() { return currentParticipants; }
    public ScheduleStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
