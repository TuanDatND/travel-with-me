package com.coc.sba_treektour.modules.schedule.entity;

import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotAvailableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "registration_closes_at", nullable = false)
    private OffsetDateTime registrationClosesAt;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    @Column(name = "current_participants", nullable = false)
    private Integer currentParticipants = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ScheduleStatus status = ScheduleStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public EventSchedule(Long eventId, Long branchId, Long guideId,
                         OffsetDateTime startDatetime, OffsetDateTime endDatetime,
                         Integer maxParticipants) {
        this.eventId = eventId;
        this.branchId = branchId;
        this.guideId = guideId;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.registrationClosesAt = startDatetime;
        this.maxParticipants = maxParticipants;
        this.currentParticipants = 0;
        this.status = ScheduleStatus.OPEN;
        this.createdAt = OffsetDateTime.now();
    }

    public void updateDetails(Long guideId, Integer maxParticipants) {
        if (maxParticipants == null || maxParticipants < 1 || maxParticipants < currentParticipants)
            throw new IllegalArgumentException("Sức chứa phải dương và không nhỏ hơn số chỗ đã giữ");
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

    public void setRegistrationClosesAt(OffsetDateTime cutoff) {
        if (cutoff == null || cutoff.isAfter(startDatetime))
            throw new IllegalArgumentException("Hạn đăng ký không được sau giờ khởi hành");
        this.registrationClosesAt = cutoff;
    }

    /** Caller must hold the schedule write lock for the whole transaction. */
    public void reserve(OffsetDateTime now) {
        if (status != ScheduleStatus.OPEN || !now.isBefore(registrationClosesAt) || currentParticipants >= maxParticipants)
            throw new ScheduleNotAvailableException("Lịch đã đóng đăng ký hoặc hết chỗ");
        currentParticipants++;
        if (currentParticipants.equals(maxParticipants)) status = ScheduleStatus.FULL;
    }

    public void release() {
        if (currentParticipants <= 0) throw new IllegalStateException("Sức chứa không nhất quán");
        currentParticipants--;
        if (status == ScheduleStatus.FULL) status = ScheduleStatus.OPEN;
    }

}
