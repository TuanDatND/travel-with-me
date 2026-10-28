package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.exception.InvalidScheduleOperationException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotFoundException;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.dto.*;
import com.coc.sba_treektour.modules.schedule.entity.*;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleConflictException;
import com.coc.sba_treektour.modules.schedule.repository.*;
import com.coc.sba_treektour.modules.schedule.security.ScheduleAccessPolicy;
import com.coc.sba_treektour.modules.schedule.service.*;
import com.coc.sba_treektour.modules.tour.enums.EventStatus;
import com.coc.sba_treektour.modules.tour.service.EventService;
import com.coc.sba_treektour.modules.tour.enums.GuideStatus;
import com.coc.sba_treektour.modules.tour.service.GuideService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyAuthority('ADMIN','STAFF')")
public class ScheduleServiceImpl implements ScheduleService {
    private final EventScheduleRepository schedules;
    private final EventScheduleHistoryRepository histories;
    private final EventParticipantRepository participants;
    private final EventService events;
    private final GuideService guides;
    private final ScheduleAccessPolicy access;
    private final ScheduleIntegrationRepository integration;
    private final ScheduleSettings settings;
    private final ParticipantLifecycle lifecycle;
    private final GuideScheduleRepository guideSchedules;

    public ScheduleServiceImpl(EventScheduleRepository schedules, EventScheduleHistoryRepository histories,
            EventParticipantRepository participants, EventService events, GuideService guides,
            ScheduleAccessPolicy access, ScheduleIntegrationRepository integration, ScheduleSettings settings,
            ParticipantLifecycle lifecycle, GuideScheduleRepository guideSchedules) {
        this.schedules = schedules; this.histories = histories; this.participants = participants;
        this.events = events; this.guides = guides; this.access = access;
        this.integration = integration; this.settings = settings; this.lifecycle = lifecycle;
        this.guideSchedules = guideSchedules;
    }

    @Override
    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        access.requireManager(request.branchId());
        requireDates(request.startDatetime(), request.endDatetime());
        requirePublished(request.eventId());
        requireBranch(request.branchId());
        validateAssignment(request.guideId(), request.startDatetime(), request.endDatetime(), null);
        if (request.maxParticipants() == null || request.maxParticipants() < 1)
            throw new InvalidScheduleOperationException("Sức chứa phải lớn hơn 0");
        var schedule = new EventSchedule(request.eventId(), request.branchId(), request.guideId(),
                request.startDatetime(), request.endDatetime(), request.maxParticipants());
        var cutoff = request.registrationClosesAt() == null ? request.startDatetime() : request.registrationClosesAt();
        requireCutoff(cutoff, request.startDatetime());
        schedule.setRegistrationClosesAt(cutoff);
        schedules.save(schedule);
        record(schedule, null, schedule.getStatus(), null, null, "Tạo lịch");
        return toResponse(schedule);
    }

    @Override
    @PreAuthorize("permitAll()")
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        var schedule = find(id);
        if (!access.manages(schedule.getBranchId()) && events.reference(schedule.getEventId()).status() != EventStatus.PUBLISHED)
            throw new ScheduleNotFoundException(id);
        return toResponse(schedule);
    }

    @Override
    @PreAuthorize("permitAll()")
    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll(Long eventId, Long branchId, ScheduleStatus status, int page, int size) {
        Long staffId = access.hasRole("STAFF") ? access.userId() : -1L;
        return schedules.search(eventId, branchId, status == null ? null : status.name(),
                access.hasRole("ADMIN"), staffId, SchedulePages.of(page, size)).stream().map(this::toResponse).toList();
    }

    @Override
    @PreAuthorize("hasAuthority('GUIDE')")
    @Transactional(readOnly = true)
    public List<GuideScheduleResponse> findAssigned(ScheduleStatus status, int page, int size) {
        return guideSchedules.findAssigned(access.userId(), null, status, SchedulePages.of(page, size));
    }

    @Override
    @PreAuthorize("hasAuthority('GUIDE')")
    @Transactional(readOnly = true)
    public GuideScheduleResponse findAssignedById(Long id) {
        var schedule = find(id);
        access.requireAssignedGuideOrManager(schedule);
        return guideSchedules.findAssigned(access.userId(), id, null, SchedulePages.of(0, 1)).stream()
                .findFirst().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Lịch không còn được phân công cho bạn"));
    }

    @Override
    @Transactional
    public ScheduleResponse update(Long id, UpdateScheduleRequest request) {
        var schedule = locked(id);
        access.requireManager(schedule.getBranchId());
        ScheduleLifecycle.requireEditable(schedule, settings.now());
        lifecycle.expire(schedule, settings.now());
        requirePublished(schedule.getEventId());
        requireBranch(schedule.getBranchId());
        validateAssignment(request.guideId(), schedule.getStartDatetime(), schedule.getEndDatetime(), id);
        if (request.maxParticipants() == null || request.maxParticipants() < 1
                || request.maxParticipants() < schedule.getCurrentParticipants())
            throw new InvalidScheduleOperationException("Sức chứa phải dương và không nhỏ hơn số chỗ đang giữ");
        var oldStatus = schedule.getStatus();
        String reason = "Đổi guide " + schedule.getGuideId() + " -> " + request.guideId()
                + "; sức chứa " + schedule.getMaxParticipants() + " -> " + request.maxParticipants();
        schedule.updateDetails(request.guideId(), request.maxParticipants());
        record(schedule, oldStatus, schedule.getStatus(), null, null, reason);
        return toResponse(schedule);
    }

    @Override
    @Transactional
    public ScheduleResponse reschedule(Long id, RescheduleRequest request) {
        var schedule = locked(id);
        access.requireManager(schedule.getBranchId());
        ScheduleLifecycle.requireEditable(schedule, settings.now());
        requireDates(request.newStartDatetime(), request.newEndDatetime());
        requireReason(request.changeReason());
        requirePublished(schedule.getEventId());
        requireBranch(schedule.getBranchId());
        validateAssignment(schedule.getGuideId(), request.newStartDatetime(), request.newEndDatetime(), id);
        var cutoff = request.registrationClosesAt() == null
                ? request.newStartDatetime().minus(Duration.between(schedule.getRegistrationClosesAt(), schedule.getStartDatetime()))
                : request.registrationClosesAt();
        requireCutoff(cutoff, request.newStartDatetime());
        var oldStart = schedule.getStartDatetime();
        var oldEnd = schedule.getEndDatetime();
        lifecycle.expire(schedule, settings.now());
        schedule.reschedule(request.newStartDatetime(), request.newEndDatetime());
        schedule.setRegistrationClosesAt(cutoff);
        // Moving the deadline earlier must not leave holds beyond the new deadline.
        for (var participant : participants.findByScheduleId(id)) participant.shortenHold(cutoff);
        record(schedule, schedule.getStatus(), schedule.getStatus(), oldStart, oldEnd, request.changeReason());
        return toResponse(schedule);
    }

    @Override
    @Transactional
    public ScheduleResponse updateStatus(Long id, UpdateScheduleStatusRequest request) {
        var schedule = locked(id);
        access.requireManager(schedule.getBranchId());
        requireReason(request.changeReason());
        lifecycle.expire(schedule, settings.now());
        var old = schedule.getStatus();
        var target = ScheduleLifecycle.transition(schedule, request.status(), settings.now());
        if (old == target) return toResponse(schedule);
        if (target == ScheduleStatus.OPEN || target == ScheduleStatus.FULL || target == ScheduleStatus.IN_PROGRESS) {
            requirePublished(schedule.getEventId());
            requireBranch(schedule.getBranchId());
            validateAssignment(schedule.getGuideId(), schedule.getStartDatetime(), schedule.getEndDatetime(), id);
        }
        if (target == ScheduleStatus.CANCELLED) {
            for (var p : participants.findByScheduleId(id)) {
                if (p.occupiesSeat()) lifecycle.cancel(p, access.userId(), request.changeReason());
            }
        }
        if (target == ScheduleStatus.COMPLETED) {
            for (var p : participants.findByScheduleId(id)) {
                if (p.getParticipantStatus() == ParticipantStatus.CONFIRMED) {
                    var previous = p.getParticipantStatus();
                    if (p.getCheckInStatus() == CheckInStatus.CHECKED_IN) p.complete(); else p.markNoShow();
                    lifecycle.audit(p, "FINISH_TOUR", previous, access.userId(), request.changeReason());
                }
            }
        }
        schedule.updateStatus(target);
        record(schedule, old, target, null, null, request.changeReason());
        return toResponse(schedule);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF','GUIDE')")
    @Transactional(readOnly = true)
    public List<ScheduleHistoryResponse> getHistories(Long id, int page, int size) {
        access.requireAssignedGuideOrManager(find(id));
        return histories.findByScheduleIdOrderByChangedAtDescIdDesc(id, SchedulePages.of(page, size)).stream()
                .map(h -> new ScheduleHistoryResponse(h.getId(), id, h.getOldStartDatetime(), h.getNewStartDatetime(),
                        h.getOldEndDatetime(), h.getNewEndDatetime(), h.getOldStatus(), h.getNewStatus(),
                        h.getChangeReason(), h.getChangedAt(), h.getChangedBy())).toList();
    }

    private void requirePublished(Long id) {
        if (events.reference(id).status() != EventStatus.PUBLISHED) throw new ScheduleConflictException("Tour phải PUBLISHED");
    }

    private void requireBranch(Long id) {
        if (!integration.branchActive(id)) throw new InvalidScheduleOperationException("Chi nhánh không tồn tại hoặc không hoạt động");
    }

    private void requireDates(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null || !start.isAfter(settings.now()) || !end.isAfter(start))
            throw new InvalidScheduleOperationException("Ngày khởi hành phải ở tương lai và kết thúc phải sau khởi hành");
    }

    private void requireCutoff(OffsetDateTime cutoff, OffsetDateTime start) {
        if (!cutoff.isAfter(settings.now()) || cutoff.isAfter(start))
            throw new InvalidScheduleOperationException("Hạn đăng ký phải ở tương lai và không sau giờ khởi hành");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new InvalidScheduleOperationException("Phải cung cấp lý do");
    }

    private void validateAssignment(Long guideId, OffsetDateTime start, OffsetDateTime end, Long excludeId) {
        if (guideId == null) return;
        var guide = guides.reference(guideId);
        integration.lockGuide(guide.userId());
        if (!guide.accountActive() || guide.status() != GuideStatus.ACTIVE)
            throw new ScheduleConflictException("Guide hoặc tài khoản không hoạt động");
        if (schedules.overlaps(guide.userId(), excludeId, start, end)) throw new ScheduleConflictException("Guide đã có lịch trùng");
    }

    private EventSchedule find(Long id) { return schedules.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id)); }
    private EventSchedule locked(Long id) { return schedules.findForUpdate(id).orElseThrow(() -> new ScheduleNotFoundException(id)); }

    private void record(EventSchedule s, ScheduleStatus old, ScheduleStatus target, OffsetDateTime oldStart,
            OffsetDateTime oldEnd, String reason) {
        var history = new EventScheduleHistory(s, oldStart, oldStart == null ? null : s.getStartDatetime(),
                oldEnd, oldEnd == null ? null : s.getEndDatetime(), old == null ? null : old.name(), target.name(), reason);
        history.setChangedBy(access.userId());
        histories.save(history);
    }

    private ScheduleResponse toResponse(EventSchedule s) {
        return new ScheduleResponse(s.getId(), s.getEventId(), s.getBranchId(), s.getGuideId(), s.getStartDatetime(),
                s.getEndDatetime(), s.getMaxParticipants(), s.getCurrentParticipants(), s.getStatus(), s.getCreatedAt(),
                s.getRegistrationClosesAt());
    }
}
