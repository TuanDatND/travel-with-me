package com.coc.sba_treektour.schedule.service;

import com.coc.sba_treektour.schedule.dto.ParticipantResponse;
import com.coc.sba_treektour.schedule.dto.RegisterParticipantRequest;
import com.coc.sba_treektour.schedule.entity.CheckInStatus;
import com.coc.sba_treektour.schedule.entity.EventParticipant;
import com.coc.sba_treektour.schedule.entity.EventSchedule;
import com.coc.sba_treektour.schedule.entity.ParticipantStatus;
import com.coc.sba_treektour.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.tour.entity.EventStatus;
import com.coc.sba_treektour.tour.service.EventService;
import com.coc.sba_treektour.tour.service.TourException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ParticipantService {

    private final EventParticipantRepository participantRepository;
    private final EventScheduleRepository scheduleRepository;
    private final EventService events;
    private final ScheduleAccess access;

    /** Khởi tạo dịch vụ giữ chỗ, sử dụng giá tour và danh tính JWT. */
    public ParticipantService(
            EventParticipantRepository participantRepository,
            EventScheduleRepository scheduleRepository,
            EventService events,
            ScheduleAccess access) {
        this.participantRepository = participantRepository;
        this.scheduleRepository = scheduleRepository;
        this.events = events;
        this.access = access;
    }

    /** Giữ chỗ nguyên tử cho tài khoản JWT; giá lấy từ tour, chỉ đăng ký lại vé đã hủy. */
    @Transactional
    public ParticipantResponse register(Long scheduleId, RegisterParticipantRequest request) {
        Long userId = access.userId();
        EventSchedule locked =
                scheduleRepository
                        .findForUpdate(scheduleId)
                        .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        var event = events.reference(locked.getEventId());
        if (event.status() != EventStatus.PUBLISHED)
            throw new ScheduleNotAvailableException("Tour chưa được công bố hoặc đã lưu trữ");
        if (!locked.getStartDatetime().isAfter(OffsetDateTime.now()))
            throw new ScheduleNotAvailableException("Lịch tour đã khởi hành");
        var price = event.basePrice();
        Optional<EventParticipant> existingOpt =
                participantRepository.findByScheduleIdAndUserId(scheduleId, userId);

        if (existingOpt.isPresent()) {
            ParticipantStatus status = existingOpt.get().getParticipantStatus();
            if (status != ParticipantStatus.CANCELLED) {
                throw new DuplicateRegistrationException(
                        "User has already registered for this schedule with status: " + status);
            }
        }

        // Atomic update: tăng số người tham gia nếu schedule đang OPEN và current < max
        int updated = scheduleRepository.incrementParticipantsIfAvailable(scheduleId);
        if (updated == 0) {
            EventSchedule schedule =
                    scheduleRepository
                            .findById(scheduleId)
                            .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
            throw new ScheduleNotAvailableException(
                    "Tour schedule is not available for registration (Current status: "
                            + schedule.getStatus()
                            + ", Capacity: "
                            + schedule.getCurrentParticipants()
                            + "/"
                            + schedule.getMaxParticipants()
                            + ")");
        }

        EventSchedule schedule =
                scheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        EventParticipant participant;
        if (existingOpt.isPresent()) {
            // Re-registration: cập nhật lại bản ghi cũ theo ràng buộc unique (schedule_id, user_id)
            participant = existingOpt.get();
            participant.reRegister(price, request.note());
        } else {
            participant = new EventParticipant(schedule, userId, price, request.note());
        }

        return toResponse(participantRepository.save(participant));
    }

    /** Hủy đăng ký và giải phóng slot bằng câu lệnh Atomic decrement */
    @Transactional
    public ParticipantResponse cancel(Long participantId) {
        EventParticipant participant = findForUpdate(participantId);

        access.requireOwnerOrManager(participant.getUserId());

        if (participant.getParticipantStatus() == ParticipantStatus.CANCELLED) {
            return toResponse(participant);
        }

        if (participant.getParticipantStatus() == ParticipantStatus.COMPLETED) {
            throw new InvalidScheduleOperationException(
                    "Cannot cancel a completed tour participation");
        }

        if (participant.getParticipantStatus() == ParticipantStatus.NO_SHOW
                || !participant.getSchedule().getStartDatetime().isAfter(OffsetDateTime.now()))
            throw new InvalidScheduleOperationException(
                    "Không thể hủy vé sau khi tour khởi hành hoặc khách vắng mặt");
        participant.cancel();
        scheduleRepository.decrementParticipants(participant.getSchedule().getId());

        return toResponse(participant);
    }

    /** Xác nhận thủ công bởi ADMIN/STAFF; callback thanh toán chưa tích hợp. */
    @Transactional
    public ParticipantResponse confirmPayment(Long participantId) {
        EventParticipant participant = findForUpdate(participantId);

        if (!access.manager())
            throw new TourException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        if (participant.getParticipantStatus() != ParticipantStatus.PENDING) {
            throw new InvalidScheduleOperationException(
                    "Only PENDING participants can be confirmed. Current: "
                            + participant.getParticipantStatus());
        }

        participant.confirmPayment();
        return toResponse(participant);
    }

    /** Hướng dẫn viên / Nhân viên điểm danh khách hàng */
    @Transactional
    public ParticipantResponse checkIn(Long participantId) {
        EventParticipant participant = findForUpdate(participantId);

        access.requireAssignedGuideOrManager(participant.getSchedule());
        if (participant.getParticipantStatus() != ParticipantStatus.CONFIRMED) {
            throw new InvalidScheduleOperationException(
                    "Only CONFIRMED participants can check in. Current: "
                            + participant.getParticipantStatus());
        }

        participant.checkIn();
        return toResponse(participant);
    }

    /** Đánh dấu khách vắng mặt không lý do */
    @Transactional
    public ParticipantResponse markNoShow(Long participantId) {
        EventParticipant participant = findForUpdate(participantId);

        access.requireAssignedGuideOrManager(participant.getSchedule());
        if (participant.getParticipantStatus() != ParticipantStatus.CONFIRMED
                || participant.getCheckInStatus() == CheckInStatus.CHECKED_IN) {
            throw new InvalidScheduleOperationException(
                    "Cannot mark as NO_SHOW for current participant state");
        }

        participant.markNoShow();
        return toResponse(participant);
    }

    /** Đọc vé khi là chủ vé hoặc quản lý. */
    @Transactional(readOnly = true)
    public ParticipantResponse findById(Long id) {
        var participant = findEntity(id);
        access.requireOwnerOrManager(participant.getUserId());
        return toResponse(participant);
    }

    /** Cho quản lý hoặc guide được phân công đọc danh sách khách. */
    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByScheduleId(Long scheduleId) {
        access.requireAssignedGuideOrManager(
                scheduleRepository
                        .findById(scheduleId)
                        .orElseThrow(() -> new ScheduleNotFoundException(scheduleId)));
        return participantRepository.findByScheduleId(scheduleId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Đọc danh sách vé của chính tài khoản hoặc theo quyền quản lý. */
    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByUserId(Long userId) {
        access.requireOwnerOrManager(userId);
        return participantRepository.findByUserIdOrderByRegistrationDateDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Luôn khóa lịch trước vé; tránh hủy hai lần trừ sức chứa hoặc đăng ký lại ghi đè. */
    private EventParticipant findForUpdate(Long id) {
        Long scheduleId =
                participantRepository
                        .findScheduleId(id)
                        .orElseThrow(() -> new ParticipantNotFoundException(id));
        scheduleRepository
                .findForUpdate(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        return participantRepository
                .findForUpdate(id)
                .orElseThrow(() -> new ParticipantNotFoundException(id));
    }

    /** Tìm vé theo ID hoặc trả 404. */
    private EventParticipant findEntity(Long id) {
        return participantRepository
                .findById(id)
                .orElseThrow(() -> new ParticipantNotFoundException(id));
    }

    /** Trả ID vé, lịch, người dùng, giá chốt và trạng thái. */
    private ParticipantResponse toResponse(EventParticipant p) {
        return new ParticipantResponse(
                p.getId(),
                p.getSchedule().getId(),
                p.getUserId(),
                p.getRegistrationDate(),
                p.getParticipantStatus(),
                p.getCheckInStatus(),
                p.getRegisteredPrice(),
                p.getNote());
    }
}
