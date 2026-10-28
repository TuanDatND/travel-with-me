package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import com.coc.sba_treektour.modules.schedule.entity.ParticipantHistory;
import com.coc.sba_treektour.modules.schedule.entity.SchedulePaymentReview;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.schedule.repository.ParticipantHistoryRepository;
import com.coc.sba_treektour.modules.schedule.repository.ScheduleIntegrationRepository;
import com.coc.sba_treektour.modules.schedule.repository.SchedulePaymentReviewRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal transactional operations. Always called after acquiring the schedule write lock. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class ParticipantLifecycle {
    private final EventParticipantRepository eventParticipantRepository;
    private final ParticipantHistoryRepository participantHistoryRepository;
    private final SchedulePaymentReviewRepository schedulePaymentReviewRepository;
    private final ScheduleIntegrationRepository scheduleIntegrationRepository;
    private final ScheduleSettings scheduleSettings;

    public void expire(EventSchedule schedule, OffsetDateTime now) {
        for (var p : eventParticipantRepository.findByScheduleId(schedule.getId())) {
            if (p.expire(now)) {
                schedule.release();
                audit(p, "EXPIRE_HOLD", ParticipantStatus.PENDING, null, "Hết hạn giữ chỗ");
                reviewTransactions(p, "Vé hết hạn; đối soát thanh toán đang xử lý hoặc đã nhận");
            }
        }
    }

    public void cancel(EventParticipant p, Long actor, String reason) {
        var old = p.getParticipantStatus();
        p.cancel();
        p.getSchedule().release();
        audit(p, "CANCEL", old, actor, reason);
        reviewTransactions(p, reason);
        if (old == ParticipantStatus.CONFIRMED && (p.getConfirmationReference() == null
                || !p.getConfirmationReference().startsWith("TRANSACTION:"))) {
            review(p, p.getRegistrationReference(), null, "REFUND_REVIEW", "Xác nhận thủ công: "
                    + p.getConfirmationReference() + "; " + reason);
        }
    }

    public void audit(EventParticipant p, String action, ParticipantStatus oldStatus, Long actor, String reason) {
        participantHistoryRepository.save(new ParticipantHistory(p, action, oldStatus, actor, reason, scheduleSettings.now()));
    }

    public boolean hasPaymentReview(Long transactionId) {
        return schedulePaymentReviewRepository.existsByDedupKey("TRANSACTION:" + transactionId);
    }

    public void review(EventParticipant p, UUID reference, Long transactionId, String kind, String reason) {
        review(p, reference, transactionId, p.getRegisteredPrice(), kind, reason);
    }

    public void review(EventParticipant p, UUID reference, Long transactionId, BigDecimal amount, String kind, String reason) {
        String key = transactionId == null ? p.getId() + ":" + reference + ":MANUAL" : "TRANSACTION:" + transactionId;
        if (!schedulePaymentReviewRepository.existsByDedupKey(key))
            schedulePaymentReviewRepository.save(new SchedulePaymentReview(p, reference, transactionId, key, kind, amount, reason, scheduleSettings.now()));
    }

    private void reviewTransactions(EventParticipant p, String reason) {
        for (var payment : scheduleIntegrationRepository.payments(p.getId())) {
            if (payment.createdAt().isBefore(p.getRegistrationDate())) continue;
            if (payment.status().equals("SUCCEEDED") || payment.status().equals("PENDING"))
                review(p, p.getRegistrationReference(), payment.id(), payment.amount(), "REFUND_REVIEW", reason);
        }
    }
}
