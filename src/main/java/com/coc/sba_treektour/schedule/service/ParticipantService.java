package com.coc.sba_treektour.schedule.service;

import com.coc.sba_treektour.schedule.dto.ParticipantResponse;
import com.coc.sba_treektour.schedule.dto.RegisterParticipantRequest;
import com.coc.sba_treektour.schedule.entity.CheckInStatus;
import com.coc.sba_treektour.schedule.entity.EventParticipant;
import com.coc.sba_treektour.schedule.entity.EventSchedule;
import com.coc.sba_treektour.schedule.entity.ParticipantStatus;
import com.coc.sba_treektour.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.schedule.repository.EventScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ParticipantService {

    private final EventParticipantRepository participantRepository;
    private final EventScheduleRepository scheduleRepository;

    public ParticipantService(EventParticipantRepository participantRepository,
                              EventScheduleRepository scheduleRepository) {
        this.participantRepository = participantRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Đăng ký tham gia tour trekking sử dụng Atomic Update kết hợp @Transactional
     * để kiểm tra sức chứa và tăng current_participants trong 1 câu SQL an toàn tuyệt đối.
     * Hỗ trợ quy tắc Re-registration của CSDL khi khách từng hủy trước đó.
     */
    @Transactional
    public ParticipantResponse register(Long scheduleId, RegisterParticipantRequest request) {
        Optional<EventParticipant> existingOpt = participantRepository.findByScheduleIdAndUserId(scheduleId, request.userId());

        if (existingOpt.isPresent()) {
            ParticipantStatus status = existingOpt.get().getParticipantStatus();
            if (status == ParticipantStatus.CONFIRMED || status == ParticipantStatus.PENDING) {
                throw new DuplicateRegistrationException("User has already registered for this schedule with active status: " + status);
            }
        }

        // Atomic update: tăng số người tham gia nếu schedule đang OPEN và current < max
        int updated = scheduleRepository.incrementParticipantsIfAvailable(scheduleId);
        if (updated == 0) {
            EventSchedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
            throw new ScheduleNotAvailableException(
                    "Tour schedule is not available for registration (Current status: " + schedule.getStatus() +
                    ", Capacity: " + schedule.getCurrentParticipants() + "/" + schedule.getMaxParticipants() + ")"
            );
        }

        EventSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        EventParticipant participant;
        if (existingOpt.isPresent()) {
            // Re-registration: cập nhật lại bản ghi cũ theo ràng buộc unique (schedule_id, user_id)
            participant = existingOpt.get();
            participant.reRegister(request.registeredPrice(), request.note());
        } else {
            participant = new EventParticipant(schedule, request.userId(), request.registeredPrice(), request.note());
        }

        return toResponse(participantRepository.save(participant));
    }

    /**
     * Hủy đăng ký và giải phóng slot bằng câu lệnh Atomic decrement
     */
    @Transactional
    public ParticipantResponse cancel(Long participantId) {
        EventParticipant participant = findEntity(participantId);

        if (participant.getParticipantStatus() == ParticipantStatus.CANCELLED) {
            return toResponse(participant);
        }

        if (participant.getParticipantStatus() == ParticipantStatus.COMPLETED) {
            throw new InvalidScheduleOperationException("Cannot cancel a completed tour participation");
        }

        participant.cancel();
        scheduleRepository.decrementParticipants(participant.getSchedule().getId());

        return toResponse(participant);
    }

    /**
     * Xác nhận thanh toán thành công (Module 6 gọi sang hoặc thủ công)
     */
    @Transactional
    public ParticipantResponse confirmPayment(Long participantId) {
        EventParticipant participant = findEntity(participantId);

        if (participant.getParticipantStatus() != ParticipantStatus.PENDING) {
            throw new InvalidScheduleOperationException("Only PENDING participants can be confirmed. Current: " + participant.getParticipantStatus());
        }

        participant.confirmPayment();
        return toResponse(participant);
    }

    /**
     * Hướng dẫn viên / Nhân viên điểm danh khách hàng
     */
    @Transactional
    public ParticipantResponse checkIn(Long participantId) {
        EventParticipant participant = findEntity(participantId);

        if (participant.getParticipantStatus() != ParticipantStatus.CONFIRMED) {
            throw new InvalidScheduleOperationException("Only CONFIRMED participants can check in. Current: " + participant.getParticipantStatus());
        }

        participant.checkIn();
        return toResponse(participant);
    }

    /**
     * Đánh dấu khách vắng mặt không lý do
     */
    @Transactional
    public ParticipantResponse markNoShow(Long participantId) {
        EventParticipant participant = findEntity(participantId);

        if (participant.getParticipantStatus() != ParticipantStatus.CONFIRMED || participant.getCheckInStatus() == CheckInStatus.CHECKED_IN) {
            throw new InvalidScheduleOperationException("Cannot mark as NO_SHOW for current participant state");
        }

        participant.markNoShow();
        return toResponse(participant);
    }

    @Transactional(readOnly = true)
    public ParticipantResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByScheduleId(Long scheduleId) {
        return participantRepository.findByScheduleId(scheduleId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> findByUserId(Long userId) {
        return participantRepository.findByUserIdOrderByRegistrationDateDesc(userId).stream().map(this::toResponse).toList();
    }

    private EventParticipant findEntity(Long id) {
        return participantRepository.findById(id).orElseThrow(() -> new ParticipantNotFoundException(id));
    }

    private ParticipantResponse toResponse(EventParticipant p) {
        return new ParticipantResponse(
                p.getId(),
                p.getSchedule().getId(),
                p.getUserId(),
                p.getRegistrationDate(),
                p.getParticipantStatus(),
                p.getCheckInStatus(),
                p.getRegisteredPrice(),
                p.getNote()
        );
    }
}
