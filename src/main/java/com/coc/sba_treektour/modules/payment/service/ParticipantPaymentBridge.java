package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;

public interface ParticipantPaymentBridge {
    EventParticipant lock(Long id);
    void requireOwner(EventParticipant ticket);
    EventParticipant prepare(Long id);
    int lifetimeSeconds(EventParticipant ticket);
    String confirm(Transaction payment);
}
