package com.coc.sba_treektour.modules.schedule.service;

import com.coc.sba_treektour.modules.schedule.dto.*;
import java.util.List;

public interface ParticipantService {
    ParticipantResponse register(Long scheduleId, RegisterParticipantRequest request);
    ParticipantResponse cancel(Long id, CancelParticipantRequest request);
    ParticipantResponse confirmPayment(Long id, ConfirmParticipantRequest request);
    ParticipantResponse checkIn(Long id);
    ParticipantResponse markNoShow(Long id);
    ParticipantResponse findById(Long id);
    List<ParticipantResponse> findByScheduleId(Long id, int page, int size);
    List<ParticipantResponse> findByUserId(Long id, int page, int size);
    List<ParticipantHistoryResponse> getHistories(Long id, int page, int size);
    List<PaymentReviewResponse> paymentReviews(Long branchId, int page, int size);
}
