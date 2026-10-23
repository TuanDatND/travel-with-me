package com.coc.sba_treektour.modules.schedule.service;

import java.util.UUID;

/** Internal Payment contract. No public HTTP endpoint; caller supplies a trusted service identity. */
public interface ParticipantPaymentService {
    ConfirmationResult confirm(Long participantId, UUID registrationReference, Long transactionId);
    enum ConfirmationResult { CONFIRMED, REVIEW_REQUIRED }
}
