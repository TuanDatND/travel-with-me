package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.exception.ParticipantNotFoundException;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleNotFoundException;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.entity.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleConflictException;
import com.coc.sba_treektour.modules.schedule.repository.*;
import com.coc.sba_treektour.modules.schedule.service.*;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantPaymentServiceImpl implements ParticipantPaymentService {
    private final EventParticipantRepository participants;
    private final EventScheduleRepository schedules;
    private final ScheduleIntegrationRepository integration;
    private final ParticipantLifecycle lifecycle;
    private final ScheduleSettings settings;
    public ParticipantPaymentServiceImpl(EventParticipantRepository participants, EventScheduleRepository schedules,
            ScheduleIntegrationRepository integration, ParticipantLifecycle lifecycle, ScheduleSettings settings) {
        this.participants = participants; this.schedules = schedules; this.integration = integration;
        this.lifecycle = lifecycle; this.settings = settings;
    }

    @Override
    @PreAuthorize("hasAuthority('SCHEDULE_PAYMENT')")
    @Transactional
    public ConfirmationResult confirm(Long id, UUID registrationReference, Long transactionId) {
        if (registrationReference == null || transactionId == null) throw new ScheduleConflictException("Thiếu tham chiếu thanh toán");
        var scheduleId = participants.findScheduleId(id).orElseThrow(() -> new ParticipantNotFoundException(id));
        var schedule = schedules.findForUpdate(scheduleId).orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        var p = participants.findForUpdate(id).orElseThrow(() -> new ParticipantNotFoundException(id));
        var payment = integration.payments(id).stream().filter(t -> t.id().equals(transactionId)).findFirst()
                .orElseThrow(() -> new ScheduleConflictException("Giao dịch không thuộc vé này"));
        if (!payment.status().equals("SUCCEEDED")) throw new ScheduleConflictException("Giao dịch chưa thành công");
        lifecycle.expire(schedule, settings.now());
        String reference = "TRANSACTION:" + transactionId;
        if (p.getRegistrationReference().equals(registrationReference)
                && (p.getParticipantStatus() == ParticipantStatus.CONFIRMED || p.getParticipantStatus() == ParticipantStatus.COMPLETED
                    || p.getParticipantStatus() == ParticipantStatus.NO_SHOW)
                && reference.equals(p.getConfirmationReference())) return ConfirmationResult.CONFIRMED;
        if (lifecycle.hasPaymentReview(transactionId)) return ConfirmationResult.REVIEW_REQUIRED;
        if (!p.getRegistrationReference().equals(registrationReference) || payment.createdAt().isBefore(p.getRegistrationDate())
                || payment.amount().compareTo(p.getRegisteredPrice()) != 0
                || p.getParticipantStatus() != ParticipantStatus.PENDING || !settings.now().isBefore(p.getExpiresAt())
                || !settings.now().isBefore(schedule.getRegistrationClosesAt())
                || (schedule.getStatus() != ScheduleStatus.OPEN && schedule.getStatus() != ScheduleStatus.FULL
                    && schedule.getStatus() != ScheduleStatus.CLOSED)) {
            lifecycle.review(p, registrationReference, transactionId, payment.amount(), "LATE_PAYMENT", "Thanh toán đến muộn, sai lần đăng ký, sai số tiền hoặc vé không còn hợp lệ");
            return ConfirmationResult.REVIEW_REQUIRED;
        }
        p.confirmPayment(reference, settings.now());
        lifecycle.audit(p, "PAYMENT_CONFIRM", ParticipantStatus.PENDING, null, reference);
        return ConfirmationResult.CONFIRMED;
    }
}
