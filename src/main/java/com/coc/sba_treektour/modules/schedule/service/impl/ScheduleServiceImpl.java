package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.dto.request.CreateScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.PatchScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.RescheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.UpdateScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.UpdateScheduleStatusRequest;
import com.coc.sba_treektour.modules.schedule.dto.response.GuideScheduleResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleHistoryResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleResponse;
import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import com.coc.sba_treektour.modules.schedule.entity.EventScheduleHistory;
import com.coc.sba_treektour.modules.schedule.enums.CheckInStatus;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.exception.InvalidScheduleOperationException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleConflictException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotFoundException;
import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleHistoryRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.repository.GuideScheduleRepository;
import com.coc.sba_treektour.modules.schedule.repository.ScheduleIntegrationRepository;
import com.coc.sba_treektour.modules.schedule.security.ScheduleAccessPolicy;
import com.coc.sba_treektour.modules.schedule.service.ScheduleService;
import com.coc.sba_treektour.modules.schedule.util.SchedulePageRequests;
import com.coc.sba_treektour.modules.schedule.validator.ScheduleLifecycleRules;
import com.coc.sba_treektour.modules.tour.enums.EventStatus;
import com.coc.sba_treektour.modules.tour.enums.GuideStatus;
import com.coc.sba_treektour.modules.tour.service.EventService;
import com.coc.sba_treektour.modules.tour.service.GuideService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyAuthority('ADMIN','STAFF')")
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {
    private final EventScheduleRepository eventScheduleRepository;
    private final EventScheduleHistoryRepository eventScheduleHistoryRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventService eventService;
    private final GuideService guideService;
    private final ScheduleAccessPolicy scheduleAccessPolicy;
    private final ScheduleIntegrationRepository scheduleIntegrationRepository;
    private final ScheduleSettings scheduleSettings;
    private final ParticipantLifecycle participantLifecycle;
    private final GuideScheduleRepository guideScheduleRepository;

    @Override
    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        scheduleAccessPolicy.requireManager(request.branchId());
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
        eventScheduleRepository.save(schedule);
        record(schedule, null, schedule.getStatus(), null, null, "Tạo lịch");
        return toResponse(schedule);
    }

    @Override
    @PreAuthorize("permitAll()")
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        var schedule = find(id);
        if (!scheduleAccessPolicy.manages(schedule.getBranchId()) && eventService.reference(schedule.getEventId()).status() != EventStatus.PUBLISHED)
            throw new ScheduleNotFoundException(id);
        return toResponse(schedule);
    }

    @Override
    @PreAuthorize("permitAll()")
    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll(Long eventId, Long branchId, ScheduleStatus status, int page, int size) {
        Long staffId = scheduleAccessPolicy.hasRole("STAFF") ? scheduleAccessPolicy.userId() : -1L;
        return eventScheduleRepository.search(eventId, branchId, status == null ? null : status.name(),
                scheduleAccessPolicy.hasRole("ADMIN"), staffId, SchedulePageRequests.of(page, size)).stream().map(this::toResponse).toList();
    }

    @Override
    @PreAuthorize("hasAuthority('GUIDE')")
    @Transactional(readOnly = true)
    public List<GuideScheduleResponse> findAssigned(ScheduleStatus status, int page, int size) {
        return guideScheduleRepository.findAssigned(scheduleAccessPolicy.userId(), null, status == null ? null : status.name(), SchedulePageRequests.of(page, size))
                .stream().map(this::toGuideResponse).toList();
    }

    @Override
    @PreAuthorize("hasAuthority('GUIDE')")
    @Transactional(readOnly = true)
    public GuideScheduleResponse findAssignedById(Long id) {
        var schedule = find(id);
        scheduleAccessPolicy.requireAssignedGuideOrManager(schedule);
        return guideScheduleRepository.findAssigned(scheduleAccessPolicy.userId(), id, null, SchedulePageRequests.of(0, 1)).stream()
                .map(this::toGuideResponse).findFirst().orElseThrow(() -> new AccessDeniedException("Lịch không còn được phân công cho bạn"));
    }

    @Override
    @Transactional
    public ScheduleResponse update(Long id, UpdateScheduleRequest request) {
        return updateLocked(locked(id), request.guideId(), request.maxParticipants());
    }

    @Override
    @Transactional
    public ScheduleResponse patch(Long id, PatchScheduleRequest request) {
        var schedule = locked(id);
        return updateLocked(schedule,
                request.isGuideIdPresent() ? request.getGuideId() : schedule.getGuideId(),
                request.getMaxParticipants() == null ? schedule.getMaxParticipants() : request.getMaxParticipants());
    }

    private ScheduleResponse updateLocked(EventSchedule schedule, Long guideId, Integer maxParticipants) {
        scheduleAccessPolicy.requireManager(schedule.getBranchId());
        ScheduleLifecycleRules.requireEditable(schedule, scheduleSettings.now());
        participantLifecycle.expire(schedule, scheduleSettings.now());
        requirePublished(schedule.getEventId());
        requireBranch(schedule.getBranchId());
        validateAssignment(guideId, schedule.getStartDatetime(), schedule.getEndDatetime(), schedule.getId());
        if (maxParticipants == null || maxParticipants < 1
                || maxParticipants < schedule.getCurrentParticipants())
            throw new InvalidScheduleOperationException("Sức chứa phải dương và không nhỏ hơn số chỗ đang giữ");
        var oldStatus = schedule.getStatus();
        String reason = "Đổi guide " + schedule.getGuideId() + " -> " + guideId
                + "; sức chứa " + schedule.getMaxParticipants() + " -> " + maxParticipants;
        schedule.updateDetails(guideId, maxParticipants);
        record(schedule, oldStatus, schedule.getStatus(), null, null, reason);
        return toResponse(schedule);
    }

    @Override
    @Transactional
    public ScheduleResponse reschedule(Long id, RescheduleRequest request) {
        var schedule = locked(id);
        scheduleAccessPolicy.requireManager(schedule.getBranchId());
        ScheduleLifecycleRules.requireEditable(schedule, scheduleSettings.now());
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
        participantLifecycle.expire(schedule, scheduleSettings.now());
        schedule.reschedule(request.newStartDatetime(), request.newEndDatetime());
        schedule.setRegistrationClosesAt(cutoff);
        // Moving the deadline earlier must not leave holds beyond the new deadline.
        for (var participant : eventParticipantRepository.findByScheduleId(id)) participant.shortenHold(cutoff);
        record(schedule, schedule.getStatus(), schedule.getStatus(), oldStart, oldEnd, request.changeReason());
        return toResponse(schedule);
    }

    @Override
    @Transactional
    public ScheduleResponse updateStatus(Long id, UpdateScheduleStatusRequest request) {
        var schedule = locked(id);
        scheduleAccessPolicy.requireManager(schedule.getBranchId());
        requireReason(request.changeReason());
        participantLifecycle.expire(schedule, scheduleSettings.now());
        var old = schedule.getStatus();
        var target = ScheduleLifecycleRules.transition(schedule, request.status(), scheduleSettings.now());
        if (old == target) return toResponse(schedule);
        if (target == ScheduleStatus.OPEN || target == ScheduleStatus.FULL || target == ScheduleStatus.IN_PROGRESS) {
            requirePublished(schedule.getEventId());
            requireBranch(schedule.getBranchId());
            validateAssignment(schedule.getGuideId(), schedule.getStartDatetime(), schedule.getEndDatetime(), id);
        }
        if (target == ScheduleStatus.CANCELLED) {
            for (var p : eventParticipantRepository.findByScheduleId(id)) {
                if (p.occupiesSeat()) participantLifecycle.cancel(p, scheduleAccessPolicy.userId(), request.changeReason());
            }
        }
        if (target == ScheduleStatus.COMPLETED) {
            for (var p : eventParticipantRepository.findByScheduleId(id)) {
                if (p.getParticipantStatus() == ParticipantStatus.CONFIRMED) {
                    var previous = p.getParticipantStatus();
                    if (p.getCheckInStatus() == CheckInStatus.CHECKED_IN) p.complete(); else p.markNoShow();
                    participantLifecycle.audit(p, "FINISH_TOUR", previous, scheduleAccessPolicy.userId(), request.changeReason());
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
        scheduleAccessPolicy.requireAssignedGuideOrManager(find(id));
        return eventScheduleHistoryRepository.findByScheduleIdOrderByChangedAtDescIdDesc(id, SchedulePageRequests.of(page, size)).stream()
                .map(h -> new ScheduleHistoryResponse(h.getId(), id, h.getOldStartDatetime(), h.getNewStartDatetime(),
                        h.getOldEndDatetime(), h.getNewEndDatetime(), h.getOldStatus(), h.getNewStatus(),
                        h.getChangeReason(), h.getChangedAt(), h.getChangedBy())).toList();
    }

    private void requirePublished(Long id) {
        if (eventService.reference(id).status() != EventStatus.PUBLISHED) throw new ScheduleConflictException("Tour phải PUBLISHED");
    }

    private void requireBranch(Long id) {
        if (!scheduleIntegrationRepository.branchActive(id)) throw new InvalidScheduleOperationException("Chi nhánh không tồn tại hoặc không hoạt động");
    }

    private void requireDates(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null || !start.isAfter(scheduleSettings.now()) || !end.isAfter(start))
            throw new InvalidScheduleOperationException("Ngày khởi hành phải ở tương lai và kết thúc phải sau khởi hành");
    }

    private void requireCutoff(OffsetDateTime cutoff, OffsetDateTime start) {
        if (!cutoff.isAfter(scheduleSettings.now()) || cutoff.isAfter(start))
            throw new InvalidScheduleOperationException("Hạn đăng ký phải ở tương lai và không sau giờ khởi hành");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new InvalidScheduleOperationException("Phải cung cấp lý do");
    }

    private void validateAssignment(Long guideId, OffsetDateTime start, OffsetDateTime end, Long excludeId) {
        if (guideId == null) return;
        var guide = guideService.reference(guideId);
        scheduleIntegrationRepository.lockGuide(guide.userId());
        if (!guide.accountActive() || guide.status() != GuideStatus.ACTIVE)
            throw new ScheduleConflictException("Guide hoặc tài khoản không hoạt động");
        if (eventScheduleRepository.overlaps(guide.userId(), excludeId, start, end)) throw new ScheduleConflictException("Guide đã có lịch trùng");
    }

    private EventSchedule find(Long id) { return eventScheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id)); }
    private EventSchedule locked(Long id) { return eventScheduleRepository.findForUpdate(id).orElseThrow(() -> new ScheduleNotFoundException(id)); }

    private void record(EventSchedule s, ScheduleStatus old, ScheduleStatus target, OffsetDateTime oldStart,
            OffsetDateTime oldEnd, String reason) {
        var history = new EventScheduleHistory(s, oldStart, oldStart == null ? null : s.getStartDatetime(),
                oldEnd, oldEnd == null ? null : s.getEndDatetime(), old == null ? null : old.name(), target.name(), reason);
        history.setChangedBy(scheduleAccessPolicy.userId());
        eventScheduleHistoryRepository.save(history);
    }

    private GuideScheduleResponse toGuideResponse(GuideScheduleRepository.Assignment assignment) {
        return new GuideScheduleResponse(assignment.getId(), assignment.getEventId(), assignment.getEventName(),
                assignment.getBranchId(), assignment.getBranchName(), assignment.getGuideId(),
                assignment.getStartDatetime().atOffset(ZoneOffset.UTC), assignment.getEndDatetime().atOffset(ZoneOffset.UTC), assignment.getMaxParticipants(),
                assignment.getCurrentParticipants(), ScheduleStatus.valueOf(assignment.getStatus()),
                assignment.getCreatedAt().atOffset(ZoneOffset.UTC), assignment.getRegistrationClosesAt().atOffset(ZoneOffset.UTC));
    }

    private ScheduleResponse toResponse(EventSchedule s) {
        return new ScheduleResponse(s.getId(), s.getEventId(), s.getBranchId(), s.getGuideId(), s.getStartDatetime(),
                s.getEndDatetime(), s.getMaxParticipants(), s.getCurrentParticipants(), s.getStatus(), s.getCreatedAt(),
                s.getRegistrationClosesAt());
    }
}
