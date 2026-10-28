package com.coc.sba_treektour.modules.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_schedule_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "changed_by")
    private Long changedBy;

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

    public void setChangedBy(Long userId) { this.changedBy = userId; }
}
