package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.dto.request.CancelParticipantRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.ConfirmParticipantPaymentRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.RegisterParticipantRequest;
import com.coc.sba_treektour.modules.schedule.dto.response.ParticipantHistoryResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ParticipantResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.SchedulePaymentReviewResponse;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import com.coc.sba_treektour.modules.schedule.entity.SchedulePaymentReview;
import com.coc.sba_treektour.modules.schedule.enums.CheckInStatus;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.exception.DuplicateRegistrationException;
import com.coc.sba_treektour.modules.schedule.exception.InvalidScheduleOperationException;
import com.coc.sba_treektour.modules.schedule.exception.ParticipantNotFoundException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleConflictException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotAvailableException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotFoundException;
import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.repository.ParticipantHistoryRepository;
import com.coc.sba_treektour.modules.schedule.repository.ScheduleIntegrationRepository;
import com.coc.sba_treektour.modules.schedule.repository.SchedulePaymentReviewRepository;
import com.coc.sba_treektour.modules.schedule.security.ScheduleAccessPolicy;
import com.coc.sba_treektour.modules.schedule.service.ParticipantService;
import com.coc.sba_treektour.modules.schedule.util.SchedulePageRequests;
import com.coc.sba_treektour.modules.schedule.validator.ScheduleLifecycleRules;
import com.coc.sba_treektour.modules.tour.enums.EventStatus;
import com.coc.sba_treektour.modules.tour.service.EventService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyAuthority('ADMIN','STAFF','CUSTOMER')")
@RequiredArgsConstructor
public class ParticipantServiceImpl implements ParticipantService {
    private final EventParticipantRepository eventParticipantRepository;
    private final EventScheduleRepository eventScheduleRepository;
    private final EventService eventService;
    private final ScheduleAccessPolicy scheduleAccessPolicy;
    private final ScheduleIntegrationRepository scheduleIntegrationRepository;
    private final ScheduleSettings scheduleSettings;
    private final ParticipantLifecycle participantLifecycle;
    private final ParticipantHistoryRepository participantHistoryRepository;
    private final SchedulePaymentReviewRepository schedulePaymentReviewRepository;

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER')")
    @Transactional
    public ParticipantResponse register(Long scheduleId, RegisterParticipantRequest request) {
        scheduleAccessPolicy.requireCustomer();
        var schedule = lockedSchedule(scheduleId);
        var now = scheduleSettings.now();
        if (!scheduleIntegrationRepository.branchActive(schedule.getBranchId())) throw new ScheduleNotAvailableException("Chi nhánh không hoạt động");
        var event = eventService.reference(schedule.getEventId());
        if (event.status() != EventStatus.PUBLISHED) throw new ScheduleNotAvailableException("Tour chưa công bố hoặc đã lưu trữ");
        participantLifecycle.expire(schedule, now);
        var existing = eventParticipantRepository.findByScheduleIdAndUserId(scheduleId, scheduleAccessPolicy.userId());
        EventParticipant participant;
        var expiry = now.plus(scheduleSettings.holdDuration());
        if (expiry.isAfter(schedule.getRegistrationClosesAt())) expiry = schedule.getRegistrationClosesAt();
        if (existing.isPresent()) {
            participant = existing.get();
            var old = participant.getParticipantStatus();
            if (old != ParticipantStatus.CANCELLED && old != ParticipantStatus.EXPIRED)
                throw new DuplicateRegistrationException("Đã có vé còn hiệu lực hoặc đã tham gia lịch này");
            // Current Payment schema cannot disambiguate historical transactions across attempts.
            if (scheduleIntegrationRepository.hasTransactions(participant.getId()))
                throw new ScheduleConflictException("Vé có giao dịch cũ; cần đối soát Payment trước khi đăng ký lại");
            schedule.reserve(now);
            participant.reRegister(event.basePrice(), request.note(), now, expiry);
            participantLifecycle.audit(participant, "RE_REGISTER", old, scheduleAccessPolicy.userId(), "Đăng ký lại; chốt giá mới");
        } else {
            schedule.reserve(now);
            participant = eventParticipantRepository.save(new EventParticipant(schedule, scheduleAccessPolicy.userId(), event.basePrice(), request.note(), now, expiry));
            participantLifecycle.audit(participant, "REGISTER", null, scheduleAccessPolicy.userId(), "Giữ chỗ");
        }
        return toResponse(participant);
    }

    @Override
    @Transactional
    public ParticipantResponse cancel(Long id, CancelParticipantRequest request) {
        var p = locked(id);
        scheduleAccessPolicy.requireOwnerOrManager(p);
        if (p.getParticipantStatus() == ParticipantStatus.CANCELLED || p.getParticipantStatus() == ParticipantStatus.EXPIRED)
            return toResponse(p);
        ScheduleLifecycleRules.requireEditable(p.getSchedule(), scheduleSettings.now());
        if (!p.occupiesSeat() || p.getCheckInStatus() == CheckInStatus.CHECKED_IN)
            throw new InvalidScheduleOperationException("Không thể hủy vé đã điểm danh/kết thúc/vắng mặt");
        String reason = request == null ? null : request.reason();
        if (!p.getUserId().equals(scheduleAccessPolicy.userId()) && (reason == null || reason.isBlank()))
            throw new InvalidScheduleOperationException("Quản lý phải ghi lý do hủy thay khách");
        if (p.getParticipantStatus() == ParticipantStatus.PENDING && !scheduleSettings.now().isBefore(p.getExpiresAt())) {
            participantLifecycle.expire(p.getSchedule(), scheduleSettings.now());
            return toResponse(p);
        }
        participantLifecycle.cancel(p, scheduleAccessPolicy.userId(), reason == null || reason.isBlank() ? "Khách tự hủy" : reason);
        return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF')")
    @Transactional
    public ParticipantResponse confirmPayment(Long id, ConfirmParticipantPaymentRequest request) {
        var p = locked(id);
        scheduleAccessPolicy.requireManager(p.getSchedule().getBranchId());
        if (request == null || request.registrationReference() == null || request.paymentReference() == null
                || request.paymentReference().isBlank()) throw new InvalidScheduleOperationException("Cần mã lần đăng ký và chứng từ thanh toán");
        if (!p.getRegistrationReference().equals(request.registrationReference()))
            throw new ScheduleConflictException("Mã lần đăng ký không còn hiệu lực");
        String reference = "MANUAL:" + request.paymentReference().trim();
        if (p.getParticipantStatus() == ParticipantStatus.CONFIRMED && reference.equals(p.getConfirmationReference())) return toResponse(p);
        requirePayable(p);
        if (scheduleIntegrationRepository.hasTransactions(p.getId()))
            throw new ScheduleConflictException("Vé có giao dịch Payment; phải đối soát qua contract thanh toán");
        p.confirmPayment(reference, scheduleSettings.now());
        participantLifecycle.audit(p, "MANUAL_CONFIRM", ParticipantStatus.PENDING, scheduleAccessPolicy.userId(), reference);
        return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF','GUIDE')")
    @Transactional
    public ParticipantResponse checkIn(Long id) {
        var p = locked(id);
        scheduleAccessPolicy.requireAssignedGuideOrManager(p.getSchedule());
        requireAttendanceSchedule(p.getSchedule());
        var now = scheduleSettings.now();
        if (now.isBefore(p.getSchedule().getStartDatetime().minus(scheduleSettings.checkInBefore()))
                || now.isAfter(p.getSchedule().getStartDatetime().plus(scheduleSettings.checkInAfter())))
            throw new InvalidScheduleOperationException("Ngoài thời gian điểm danh");
        if (p.getParticipantStatus() != ParticipantStatus.CONFIRMED) throw new InvalidScheduleOperationException("Vé chưa xác nhận");
        if (p.getCheckInStatus() == CheckInStatus.CHECKED_IN) return toResponse(p);
        p.checkIn();
        participantLifecycle.audit(p, "CHECK_IN", p.getParticipantStatus(), scheduleAccessPolicy.userId(), "Điểm danh có mặt");
        return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF','GUIDE')")
    @Transactional
    public ParticipantResponse markNoShow(Long id) {
        var p = locked(id);
        scheduleAccessPolicy.requireAssignedGuideOrManager(p.getSchedule());
        requireAttendanceSchedule(p.getSchedule());
        if (!scheduleSettings.now().isAfter(p.getSchedule().getStartDatetime().plus(scheduleSettings.checkInAfter())))
            throw new InvalidScheduleOperationException("Chưa hết thời hạn điểm danh");
        if (p.getParticipantStatus() == ParticipantStatus.NO_SHOW) return toResponse(p);
        if (p.getParticipantStatus() != ParticipantStatus.CONFIRMED || p.getCheckInStatus() == CheckInStatus.CHECKED_IN)
            throw new InvalidScheduleOperationException("Không thể đánh dấu vắng mặt cho vé này");
        p.markNoShow();
        participantLifecycle.audit(p, "NO_SHOW", ParticipantStatus.CONFIRMED, scheduleAccessPolicy.userId(), "Vắng mặt sau hạn điểm danh");
        return toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipantResponse findById(Long id) {
        var p = find(id); scheduleAccessPolicy.requireOwnerOrManager(p); return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF','GUIDE')")
    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByScheduleId(Long id, int page, int size) {
        var schedule = eventScheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
        scheduleAccessPolicy.requireAssignedGuideOrManager(schedule);
        return eventParticipantRepository.findByScheduleIdOrderByIdAsc(id, SchedulePageRequests.of(page, size)).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> findMine(int page, int size) {
        return findByUserId(scheduleAccessPolicy.userId(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByUserId(Long userId, int page, int size) {
        if (userId.equals(scheduleAccessPolicy.userId()) || scheduleAccessPolicy.hasRole("ADMIN"))
            return eventParticipantRepository.findByUserIdOrderByRegistrationDateDescIdDesc(userId, SchedulePageRequests.of(page, size)).stream().map(this::toResponse).toList();
        if (!scheduleAccessPolicy.hasRole("STAFF")) throw new AccessDeniedException("Không được đọc vé người khác");
        // Filter in SQL before pagination, never fetch another branch's tickets into a response.
        return eventParticipantRepository.findVisibleUserTickets(userId, scheduleAccessPolicy.userId(), SchedulePageRequests.of(page, size)).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantHistoryResponse> getHistories(Long id, int page, int size) {
        scheduleAccessPolicy.requireOwnerOrManager(find(id));
        return participantHistoryRepository.findByParticipantIdOrderByIdDesc(id, SchedulePageRequests.of(page, size)).stream()
                .map(h -> new ParticipantHistoryResponse(h.getId(), h.getRegistrationReference(), h.getAction(), h.getOldStatus(),
                        h.getNewStatus(), h.getRegisteredPrice(), h.getChangedBy(), h.getReason(), h.getChangedAt())).toList();
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF')")
    @Transactional(readOnly = true)
    public List<SchedulePaymentReviewResponse> paymentReviews(Long branchId, int page, int size) {
        var pageable = SchedulePageRequests.of(page, size);
        List<SchedulePaymentReview> found;
        if (branchId == null && scheduleAccessPolicy.hasRole("ADMIN")) found = schedulePaymentReviewRepository.findAllByOrderByIdAsc(pageable);
        else {
            if (branchId == null) throw new InvalidScheduleOperationException("STAFF phải truyền branchId");
            scheduleAccessPolicy.requireManager(branchId);
            found = schedulePaymentReviewRepository.findByParticipantScheduleBranchIdOrderByIdAsc(branchId, pageable);
        }
        return found.stream().map(r -> new SchedulePaymentReviewResponse(r.getId(), r.getParticipant().getId(), r.getRegistrationReference(),
                r.getTransactionId(), r.getKind(), r.getAmount(), r.getReason(), r.getCreatedAt())).toList();
    }

    private void requireAttendanceSchedule(EventSchedule schedule) {
        if ((schedule.getStatus() != ScheduleStatus.CLOSED && schedule.getStatus() != ScheduleStatus.IN_PROGRESS)
                || !scheduleSettings.now().isBefore(schedule.getEndDatetime()))
            throw new InvalidScheduleOperationException("Chỉ điểm danh lịch CLOSED/IN_PROGRESS chưa kết thúc");
    }

    private void requirePayable(EventParticipant p) {
        var s = p.getSchedule();
        if (p.getParticipantStatus() != ParticipantStatus.PENDING || !scheduleSettings.now().isBefore(p.getExpiresAt())
                || !scheduleSettings.now().isBefore(s.getRegistrationClosesAt())
                || (s.getStatus() != ScheduleStatus.OPEN && s.getStatus() != ScheduleStatus.FULL && s.getStatus() != ScheduleStatus.CLOSED))
            throw new ScheduleConflictException("Vé đã hết hạn, đã xác nhận hoặc lịch không còn nhận thanh toán");
    }

    private EventSchedule lockedSchedule(Long id) { return eventScheduleRepository.findForUpdate(id).orElseThrow(() -> new ScheduleNotFoundException(id)); }
    private EventParticipant locked(Long id) {
        Long scheduleId = eventParticipantRepository.findScheduleId(id).orElseThrow(() -> new ParticipantNotFoundException(id));
        lockedSchedule(scheduleId);
        return eventParticipantRepository.findForUpdate(id).orElseThrow(() -> new ParticipantNotFoundException(id));
    }
    private EventParticipant find(Long id) { return eventParticipantRepository.findById(id).orElseThrow(() -> new ParticipantNotFoundException(id)); }

    private ParticipantResponse toResponse(EventParticipant p) {
        return new ParticipantResponse(p.getId(), p.getSchedule().getId(), p.getUserId(), p.getRegistrationDate(),
                p.getParticipantStatus(), p.getCheckInStatus(), p.getRegisteredPrice(), p.getNote(), p.getRegistrationReference(), p.getExpiresAt());
    }
}
