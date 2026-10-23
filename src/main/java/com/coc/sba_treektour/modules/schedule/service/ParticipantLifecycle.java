package com.coc.sba_treektour.modules.schedule.service;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.entity.*;
import com.coc.sba_treektour.modules.schedule.repository.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal transactional operations. Always called after acquiring the schedule write lock. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ParticipantLifecycle {
    private final EventParticipantRepository participants;
    private final ParticipantHistoryRepository histories;
    private final SchedulePaymentReviewRepository reviews;
    private final ScheduleIntegrationRepository integration;
    private final ScheduleSettings settings;

    public ParticipantLifecycle(EventParticipantRepository participants, ParticipantHistoryRepository histories,
            SchedulePaymentReviewRepository reviews, ScheduleIntegrationRepository integration, ScheduleSettings settings) {
        this.participants = participants; this.histories = histories; this.reviews = reviews;
        this.integration = integration; this.settings = settings;
    }

    public void expire(EventSchedule schedule, OffsetDateTime now) {
        for (var p : participants.findByScheduleId(schedule.getId())) {
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
        histories.save(new ParticipantHistory(p, action, oldStatus, actor, reason, settings.now()));
    }

    public boolean hasPaymentReview(Long transactionId) {
        return reviews.existsByDedupKey("TRANSACTION:" + transactionId);
    }

    public void review(EventParticipant p, UUID reference, Long transactionId, String kind, String reason) {
        review(p, reference, transactionId, p.getRegisteredPrice(), kind, reason);
    }

    public void review(EventParticipant p, UUID reference, Long transactionId, java.math.BigDecimal amount, String kind, String reason) {
        String key = transactionId == null ? p.getId() + ":" + reference + ":MANUAL" : "TRANSACTION:" + transactionId;
        if (!reviews.existsByDedupKey(key))
            reviews.save(new SchedulePaymentReview(p, reference, transactionId, key, kind, amount, reason, settings.now()));
    }

    private void reviewTransactions(EventParticipant p, String reason) {
        for (var payment : integration.payments(p.getId())) {
            if (payment.createdAt().isBefore(p.getRegistrationDate())) continue;
            if (payment.status().equals("SUCCEEDED") || payment.status().equals("PENDING"))
                review(p, p.getRegistrationReference(), payment.id(), payment.amount(), "REFUND_REVIEW", reason);
        }
    }
}
