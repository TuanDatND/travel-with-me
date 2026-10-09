package com.coc.sba_treektour.schedule.service;

import com.coc.sba_treektour.schedule.dto.*;
import com.coc.sba_treektour.schedule.entity.CheckInStatus;
import com.coc.sba_treektour.schedule.entity.EventParticipant;
import com.coc.sba_treektour.schedule.entity.EventSchedule;
import com.coc.sba_treektour.schedule.entity.EventScheduleHistory;
import com.coc.sba_treektour.schedule.entity.ScheduleStatus;
import com.coc.sba_treektour.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.schedule.repository.EventScheduleHistoryRepository;
import com.coc.sba_treektour.schedule.repository.EventScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScheduleService {

    private final EventScheduleRepository scheduleRepository;
    private final EventScheduleHistoryRepository historyRepository;
    private final EventParticipantRepository participantRepository;

    public ScheduleService(EventScheduleRepository scheduleRepository,
                           EventScheduleHistoryRepository historyRepository,
                           EventParticipantRepository participantRepository) {
        this.scheduleRepository = scheduleRepository;
        this.historyRepository = historyRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        if (!request.endDatetime().isAfter(request.startDatetime())) {
            throw new InvalidScheduleOperationException("endDatetime must be after startDatetime");
        }

        EventSchedule schedule = new EventSchedule(
                request.eventId(),
                request.branchId(),
                request.guideId(),
                request.startDatetime(),
                request.endDatetime(),
                request.maxParticipants()
        );
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll(Long eventId, Long branchId, ScheduleStatus status) {
        List<EventSchedule> schedules;
        if (eventId != null) {
            schedules = scheduleRepository.findByEventIdOrderByStartDatetimeAsc(eventId);
        } else if (branchId != null) {
            schedules = scheduleRepository.findByBranchIdOrderByStartDatetimeAsc(branchId);
        } else if (status != null) {
            schedules = scheduleRepository.findByStatusOrderByStartDatetimeAsc(status);
        } else {
            schedules = scheduleRepository.findAll();
        }
        return schedules.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ScheduleResponse update(Long id, UpdateScheduleRequest request) {
        EventSchedule schedule = findEntity(id);
        if (request.maxParticipants() < schedule.getCurrentParticipants()) {
            throw new InvalidScheduleOperationException(
                    "maxParticipants cannot be less than current participants (" + schedule.getCurrentParticipants() + ")"
            );
        }
        schedule.updateDetails(request.guideId(), request.maxParticipants());
        return toResponse(schedule);
    }

    @Transactional
    public ScheduleResponse reschedule(Long id, RescheduleRequest request) {
        if (!request.newEndDatetime().isAfter(request.newStartDatetime())) {
            throw new InvalidScheduleOperationException("newEndDatetime must be after newStartDatetime");
        }
        EventSchedule schedule = findEntity(id);

        EventScheduleHistory history = new EventScheduleHistory(
                schedule,
                schedule.getStartDatetime(),
                request.newStartDatetime(),
                schedule.getEndDatetime(),
                request.newEndDatetime(),
                schedule.getStatus().name(),
                schedule.getStatus().name(),
                request.changeReason()
        );
        historyRepository.save(history);

        schedule.reschedule(request.newStartDatetime(), request.newEndDatetime());
        return toResponse(schedule);
    }

    @Transactional
    public ScheduleResponse updateStatus(Long id, UpdateScheduleStatusRequest request) {
        EventSchedule schedule = findEntity(id);
        ScheduleStatus oldStatus = schedule.getStatus();
        ScheduleStatus newStatus = request.status();

        if (oldStatus == newStatus) {
            return toResponse(schedule);
        }

        EventScheduleHistory history = new EventScheduleHistory(
                schedule,
                null,
                null,
                null,
                null,
                oldStatus.name(),
                newStatus.name(),
                request.changeReason() == null ? "Status changed from " + oldStatus + " to " + newStatus : request.changeReason()
        );
        historyRepository.save(history);

        schedule.updateStatus(newStatus);

        // When a schedule is COMPLETED, all checked-in participants transition to COMPLETED
        if (newStatus == ScheduleStatus.COMPLETED) {
            List<EventParticipant> participants = participantRepository.findByScheduleId(id);
            for (EventParticipant p : participants) {
                if (p.getCheckInStatus() == CheckInStatus.CHECKED_IN) {
                    p.complete();
                }
            }
        }

        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public List<ScheduleHistoryResponse> getHistories(Long scheduleId) {
        findEntity(scheduleId);
        return historyRepository.findByScheduleIdOrderByChangedAtDesc(scheduleId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public EventSchedule findEntity(Long id) {
        return scheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
    }

    public ScheduleResponse toResponse(EventSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getEventId(),
                schedule.getBranchId(),
                schedule.getGuideId(),
                schedule.getStartDatetime(),
                schedule.getEndDatetime(),
                schedule.getMaxParticipants(),
                schedule.getCurrentParticipants(),
                schedule.getStatus(),
                schedule.getCreatedAt()
        );
    }

    private ScheduleHistoryResponse toHistoryResponse(EventScheduleHistory h) {
        return new ScheduleHistoryResponse(
                h.getId(),
                h.getSchedule().getId(),
                h.getOldStartDatetime(),
                h.getNewStartDatetime(),
                h.getOldEndDatetime(),
                h.getNewEndDatetime(),
                h.getOldStatus(),
                h.getNewStatus(),
                h.getChangeReason(),
                h.getChangedAt()
        );
    }
}
