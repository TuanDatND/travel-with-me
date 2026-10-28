package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.exception.ParticipantNotFoundException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleConflictException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotFoundException;
import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.repository.ScheduleIntegrationRepository;
import com.coc.sba_treektour.modules.schedule.service.ParticipantPaymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantPaymentServiceImpl implements ParticipantPaymentService {
    private final EventParticipantRepository eventParticipantRepository;
    private final EventScheduleRepository eventScheduleRepository;
    private final ScheduleIntegrationRepository scheduleIntegrationRepository;
    private final ParticipantLifecycle participantLifecycle;
    private final ScheduleSettings scheduleSettings;

    @Override
    @PreAuthorize("hasAuthority('SCHEDULE_PAYMENT')")
    @Transactional
    public ConfirmationResult confirm(Long id, UUID registrationReference, Long transactionId) {
        if (registrationReference == null || transactionId == null) throw new ScheduleConflictException("Thiếu tham chiếu thanh toán");
        var scheduleId = eventParticipantRepository.findScheduleId(id).orElseThrow(() -> new ParticipantNotFoundException(id));
        var schedule = eventScheduleRepository.findForUpdate(scheduleId).orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        var p = eventParticipantRepository.findForUpdate(id).orElseThrow(() -> new ParticipantNotFoundException(id));
        var payment = scheduleIntegrationRepository.payments(id).stream().filter(t -> t.getId().equals(transactionId)).findFirst()
                .orElseThrow(() -> new ScheduleConflictException("Giao dịch không thuộc vé này"));
        if (!payment.getStatus().equals("SUCCEEDED")) throw new ScheduleConflictException("Giao dịch chưa thành công");
        participantLifecycle.expire(schedule, scheduleSettings.now());
        String reference = "TRANSACTION:" + transactionId;
        if (p.getRegistrationReference().equals(registrationReference)
                && (p.getParticipantStatus() == ParticipantStatus.CONFIRMED || p.getParticipantStatus() == ParticipantStatus.COMPLETED
                    || p.getParticipantStatus() == ParticipantStatus.NO_SHOW)
                && reference.equals(p.getConfirmationReference())) return ConfirmationResult.CONFIRMED;
        if (participantLifecycle.hasPaymentReview(transactionId)) return ConfirmationResult.REVIEW_REQUIRED;
        if (!p.getRegistrationReference().equals(registrationReference) || payment.getCreatedAt().isBefore(p.getRegistrationDate().toInstant())
                || payment.getAmount().compareTo(p.getRegisteredPrice()) != 0
                || p.getParticipantStatus() != ParticipantStatus.PENDING || !scheduleSettings.now().isBefore(p.getExpiresAt())
                || !scheduleSettings.now().isBefore(schedule.getRegistrationClosesAt())
                || (schedule.getStatus() != ScheduleStatus.OPEN && schedule.getStatus() != ScheduleStatus.FULL
                    && schedule.getStatus() != ScheduleStatus.CLOSED)) {
            participantLifecycle.review(p, registrationReference, transactionId, payment.getAmount(), "LATE_PAYMENT", "Thanh toán đến muộn, sai lần đăng ký, sai số tiền hoặc vé không còn hợp lệ");
            return ConfirmationResult.REVIEW_REQUIRED;
        }
        p.confirmPayment(reference, scheduleSettings.now());
        participantLifecycle.audit(p, "PAYMENT_CONFIRM", ParticipantStatus.PENDING, null, reference);
        return ConfirmationResult.CONFIRMED;
    }
}
