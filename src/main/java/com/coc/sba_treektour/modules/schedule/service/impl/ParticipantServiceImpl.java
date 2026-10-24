package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.exception.DuplicateRegistrationException;
import com.coc.sba_treektour.modules.schedule.exception.InvalidScheduleOperationException;
import com.coc.sba_treektour.modules.schedule.exception.ParticipantNotFoundException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotAvailableException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotFoundException;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.dto.*;
import com.coc.sba_treektour.modules.schedule.entity.*;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleConflictException;
import com.coc.sba_treektour.modules.schedule.repository.*;
import com.coc.sba_treektour.modules.schedule.security.ScheduleAccessPolicy;
import com.coc.sba_treektour.modules.schedule.service.*;
import com.coc.sba_treektour.modules.event.enums.EventStatus;
import com.coc.sba_treektour.modules.event.service.EventService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyAuthority('ADMIN','STAFF','CUSTOMER')")
public class ParticipantServiceImpl implements ParticipantService {
    private final EventParticipantRepository participants;
    private final EventScheduleRepository schedules;
    private final EventService events;
    private final ScheduleAccessPolicy access;
    private final ScheduleIntegrationRepository integration;
    private final ScheduleSettings settings;
    private final ParticipantLifecycle lifecycle;
    private final ParticipantHistoryRepository histories;
    private final SchedulePaymentReviewRepository reviews;

    public ParticipantServiceImpl(EventParticipantRepository participants, EventScheduleRepository schedules,
            EventService events, ScheduleAccessPolicy access, ScheduleIntegrationRepository integration,
            ScheduleSettings settings, ParticipantLifecycle lifecycle, ParticipantHistoryRepository histories,
            SchedulePaymentReviewRepository reviews) {
        this.participants = participants; this.schedules = schedules; this.events = events; this.access = access;
        this.integration = integration; this.settings = settings; this.lifecycle = lifecycle;
        this.histories = histories; this.reviews = reviews;
    }

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER')")
    @Transactional
    public ParticipantResponse register(Long scheduleId, RegisterParticipantRequest request) {
        access.requireCustomer();
        var schedule = lockedSchedule(scheduleId);
        var now = settings.now();
        if (!integration.branchActive(schedule.getBranchId())) throw new ScheduleNotAvailableException("Chi nhánh không hoạt động");
        var event = events.reference(schedule.getEventId());
        if (event.status() != EventStatus.PUBLISHED) throw new ScheduleNotAvailableException("Tour chưa công bố hoặc đã lưu trữ");
        lifecycle.expire(schedule, now);
        var existing = participants.findByScheduleIdAndUserId(scheduleId, access.userId());
        EventParticipant participant;
        var expiry = now.plus(settings.holdDuration());
        if (expiry.isAfter(schedule.getRegistrationClosesAt())) expiry = schedule.getRegistrationClosesAt();
        if (existing.isPresent()) {
            participant = existing.get();
            var old = participant.getParticipantStatus();
            if (old != ParticipantStatus.CANCELLED && old != ParticipantStatus.EXPIRED)
                throw new DuplicateRegistrationException("Đã có vé còn hiệu lực hoặc đã tham gia lịch này");
            // Current Payment schema cannot disambiguate historical transactions across attempts.
            if (integration.hasTransactions(participant.getId()))
                throw new ScheduleConflictException("Vé có giao dịch cũ; cần đối soát Payment trước khi đăng ký lại");
            schedule.reserve(now);
            participant.reRegister(event.basePrice(), request.note(), now, expiry);
            lifecycle.audit(participant, "RE_REGISTER", old, access.userId(), "Đăng ký lại; chốt giá mới");
        } else {
            schedule.reserve(now);
            participant = participants.save(new EventParticipant(schedule, access.userId(), event.basePrice(), request.note(), now, expiry));
            lifecycle.audit(participant, "REGISTER", null, access.userId(), "Giữ chỗ");
        }
        return toResponse(participant);
    }

    @Override
    @Transactional
    public ParticipantResponse cancel(Long id, CancelParticipantRequest request) {
        var p = locked(id);
        access.requireOwnerOrManager(p);
        if (p.getParticipantStatus() == ParticipantStatus.CANCELLED || p.getParticipantStatus() == ParticipantStatus.EXPIRED)
            return toResponse(p);
        ScheduleLifecycle.requireEditable(p.getSchedule(), settings.now());
        if (!p.occupiesSeat() || p.getCheckInStatus() == CheckInStatus.CHECKED_IN)
            throw new InvalidScheduleOperationException("Không thể hủy vé đã điểm danh/kết thúc/vắng mặt");
        String reason = request == null ? null : request.reason();
        if (!p.getUserId().equals(access.userId()) && (reason == null || reason.isBlank()))
            throw new InvalidScheduleOperationException("Quản lý phải ghi lý do hủy thay khách");
        if (p.getParticipantStatus() == ParticipantStatus.PENDING && !settings.now().isBefore(p.getExpiresAt())) {
            lifecycle.expire(p.getSchedule(), settings.now());
            return toResponse(p);
        }
        lifecycle.cancel(p, access.userId(), reason == null || reason.isBlank() ? "Khách tự hủy" : reason);
        return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF')")
    @Transactional
    public ParticipantResponse confirmPayment(Long id, ConfirmParticipantRequest request) {
        var p = locked(id);
        access.requireManager(p.getSchedule().getBranchId());
        if (request == null || request.registrationReference() == null || request.paymentReference() == null
                || request.paymentReference().isBlank()) throw new InvalidScheduleOperationException("Cần mã lần đăng ký và chứng từ thanh toán");
        if (!p.getRegistrationReference().equals(request.registrationReference()))
            throw new ScheduleConflictException("Mã lần đăng ký không còn hiệu lực");
        String reference = "MANUAL:" + request.paymentReference().trim();
        if (p.getParticipantStatus() == ParticipantStatus.CONFIRMED && reference.equals(p.getConfirmationReference())) return toResponse(p);
        requirePayable(p);
        if (integration.hasTransactions(p.getId()))
            throw new ScheduleConflictException("Vé có giao dịch Payment; phải đối soát qua contract thanh toán");
        p.confirmPayment(reference, settings.now());
        lifecycle.audit(p, "MANUAL_CONFIRM", ParticipantStatus.PENDING, access.userId(), reference);
        return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF','GUIDE')")
    @Transactional
    public ParticipantResponse checkIn(Long id) {
        var p = locked(id);
        access.requireAssignedGuideOrManager(p.getSchedule());
        requireAttendanceSchedule(p.getSchedule());
        var now = settings.now();
        if (now.isBefore(p.getSchedule().getStartDatetime().minus(settings.checkInBefore()))
                || now.isAfter(p.getSchedule().getStartDatetime().plus(settings.checkInAfter())))
            throw new InvalidScheduleOperationException("Ngoài thời gian điểm danh");
        if (p.getParticipantStatus() != ParticipantStatus.CONFIRMED) throw new InvalidScheduleOperationException("Vé chưa xác nhận");
        if (p.getCheckInStatus() == CheckInStatus.CHECKED_IN) return toResponse(p);
        p.checkIn();
        lifecycle.audit(p, "CHECK_IN", p.getParticipantStatus(), access.userId(), "Điểm danh có mặt");
        return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF','GUIDE')")
    @Transactional
    public ParticipantResponse markNoShow(Long id) {
        var p = locked(id);
        access.requireAssignedGuideOrManager(p.getSchedule());
        requireAttendanceSchedule(p.getSchedule());
        if (!settings.now().isAfter(p.getSchedule().getStartDatetime().plus(settings.checkInAfter())))
            throw new InvalidScheduleOperationException("Chưa hết thời hạn điểm danh");
        if (p.getParticipantStatus() == ParticipantStatus.NO_SHOW) return toResponse(p);
        if (p.getParticipantStatus() != ParticipantStatus.CONFIRMED || p.getCheckInStatus() == CheckInStatus.CHECKED_IN)
            throw new InvalidScheduleOperationException("Không thể đánh dấu vắng mặt cho vé này");
        p.markNoShow();
        lifecycle.audit(p, "NO_SHOW", ParticipantStatus.CONFIRMED, access.userId(), "Vắng mặt sau hạn điểm danh");
        return toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipantResponse findById(Long id) {
        var p = find(id); access.requireOwnerOrManager(p); return toResponse(p);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF','GUIDE')")
    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByScheduleId(Long id, int page, int size) {
        var schedule = schedules.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
        access.requireAssignedGuideOrManager(schedule);
        return participants.findByScheduleIdOrderByIdAsc(id, SchedulePages.of(page, size)).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByUserId(Long userId, int page, int size) {
        if (userId.equals(access.userId()) || access.hasRole("ADMIN"))
            return participants.findByUserIdOrderByRegistrationDateDescIdDesc(userId, SchedulePages.of(page, size)).stream().map(this::toResponse).toList();
        if (!access.hasRole("STAFF")) throw new AccessDeniedException("Không được đọc vé người khác");
        // Filter in SQL before pagination, never fetch another branch's tickets into a response.
        return participants.findVisibleUserTickets(userId, access.userId(), SchedulePages.of(page, size)).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantHistoryResponse> getHistories(Long id, int page, int size) {
        access.requireOwnerOrManager(find(id));
        return histories.findByParticipantIdOrderByIdDesc(id, SchedulePages.of(page, size)).stream()
                .map(h -> new ParticipantHistoryResponse(h.getId(), h.getRegistrationReference(), h.getAction(), h.getOldStatus(),
                        h.getNewStatus(), h.getRegisteredPrice(), h.getChangedBy(), h.getReason(), h.getChangedAt())).toList();
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN','STAFF')")
    @Transactional(readOnly = true)
    public List<PaymentReviewResponse> paymentReviews(Long branchId, int page, int size) {
        var pageable = SchedulePages.of(page, size);
        List<SchedulePaymentReview> found;
        if (branchId == null && access.hasRole("ADMIN")) found = reviews.findAllByOrderByIdAsc(pageable);
        else {
            if (branchId == null) throw new InvalidScheduleOperationException("STAFF phải truyền branchId");
            access.requireManager(branchId);
            found = reviews.findByParticipantScheduleBranchIdOrderByIdAsc(branchId, pageable);
        }
        return found.stream().map(r -> new PaymentReviewResponse(r.getId(), r.getParticipant().getId(), r.getRegistrationReference(),
                r.getTransactionId(), r.getKind(), r.getAmount(), r.getReason(), r.getCreatedAt())).toList();
    }

    private void requireAttendanceSchedule(EventSchedule schedule) {
        if ((schedule.getStatus() != ScheduleStatus.CLOSED && schedule.getStatus() != ScheduleStatus.IN_PROGRESS)
                || !settings.now().isBefore(schedule.getEndDatetime()))
            throw new InvalidScheduleOperationException("Chỉ điểm danh lịch CLOSED/IN_PROGRESS chưa kết thúc");
    }

    private void requirePayable(EventParticipant p) {
        var s = p.getSchedule();
        if (p.getParticipantStatus() != ParticipantStatus.PENDING || !settings.now().isBefore(p.getExpiresAt())
                || !settings.now().isBefore(s.getRegistrationClosesAt())
                || (s.getStatus() != ScheduleStatus.OPEN && s.getStatus() != ScheduleStatus.FULL && s.getStatus() != ScheduleStatus.CLOSED))
            throw new ScheduleConflictException("Vé đã hết hạn, đã xác nhận hoặc lịch không còn nhận thanh toán");
    }

    private EventSchedule lockedSchedule(Long id) { return schedules.findForUpdate(id).orElseThrow(() -> new ScheduleNotFoundException(id)); }
    private EventParticipant locked(Long id) {
        Long scheduleId = participants.findScheduleId(id).orElseThrow(() -> new ParticipantNotFoundException(id));
        lockedSchedule(scheduleId);
        return participants.findForUpdate(id).orElseThrow(() -> new ParticipantNotFoundException(id));
    }
    private EventParticipant find(Long id) { return participants.findById(id).orElseThrow(() -> new ParticipantNotFoundException(id)); }

    private ParticipantResponse toResponse(EventParticipant p) {
        return new ParticipantResponse(p.getId(), p.getSchedule().getId(), p.getUserId(), p.getRegistrationDate(),
                p.getParticipantStatus(), p.getCheckInStatus(), p.getRegisteredPrice(), p.getNote(), p.getRegistrationReference(), p.getExpiresAt());
    }
}
