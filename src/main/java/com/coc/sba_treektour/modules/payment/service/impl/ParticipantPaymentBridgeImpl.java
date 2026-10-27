package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.ParticipantPaymentBridge;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import com.coc.sba_treektour.modules.schedule.entity.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.service.ParticipantPaymentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.PENDING;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.SUCCEEDED;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class ParticipantPaymentBridgeImpl implements ParticipantPaymentBridge {
    private final EventParticipantRepository participants;
    private final EventScheduleRepository schedules;
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final ParticipantPaymentService confirmation;
    private final EntityManager entities;

    // Caller holds a short database transaction. Global order: schedule → participant → transaction.
    @Override
    public EventParticipant lock(Long id) {
        Long scheduleId = participants.findScheduleId(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        var schedule = schedules.findForUpdate(scheduleId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Schedule not found"));
        entities.refresh(schedule, LockModeType.PESSIMISTIC_WRITE);
        var ticket = participants.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        entities.refresh(ticket, LockModeType.PESSIMISTIC_WRITE);
        return ticket;
    }

    @Override
    public void requireOwner(EventParticipant ticket) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        var owner = users.findByEmail(auth.getName()).filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Active ticket owner required"));
        if (!owner.getId().equals(ticket.getUserId())) throw new ResponseStatusException(FORBIDDEN, "You do not own this ticket");
    }

    @Override
    public EventParticipant prepare(Long id) {
        var ticket = lock(id);
        requireOwner(ticket);
        if (ticket.getParticipantStatus() != ParticipantStatus.PENDING)
            throw new ResponseStatusException(CONFLICT, "Ticket is not PENDING");
        var status = ticket.getSchedule().getStatus();
        if (status != ScheduleStatus.OPEN && status != ScheduleStatus.FULL && status != ScheduleStatus.CLOSED)
            throw new ResponseStatusException(CONFLICT, "Schedule no longer accepts payment");
        lifetimeSeconds(ticket);
        if (transactions.existsByParticipant_IdAndTransactionTypeAndStatusIn(id, "PAYMENT", List.of(PENDING, SUCCEEDED)))
            throw new ResponseStatusException(CONFLICT, "Ticket already has a pending or successful payment; reconcile it first");
        return ticket;
    }

    @Override
    public int lifetimeSeconds(EventParticipant ticket) {
        var deadline = ticket.getExpiresAt().isBefore(ticket.getSchedule().getRegistrationClosesAt())
                ? ticket.getExpiresAt() : ticket.getSchedule().getRegistrationClosesAt();
        long remaining = Duration.between(OffsetDateTime.now(), deadline).getSeconds();
        // Provider minimum is 300 seconds. Do not open a link that can outlive the ticket hold.
        if (remaining < 300) throw new ResponseStatusException(CONFLICT, "Ticket hold has less than 5 minutes remaining; cannot start payment");
        return (int) Math.min(900, remaining);
    }

    @Transactional
    @Override
    public String confirm(Transaction payment) {
        // Module 5 currently forbids re-registration once a ticket has any transaction.
        // Read its reference under the same locks used by confirmation; Module 5 also
        // checks payment.createdAt against the registration time before confirming.
        var ticket = lock(payment.getParticipantId());
        var reference = ticket.getRegistrationReference();
        if (reference == null) return "REVIEW_REQUIRED";
        var previous = SecurityContextHolder.getContext();
        var internal = SecurityContextHolder.createEmptyContext();
        internal.setAuthentication(new UsernamePasswordAuthenticationToken("payment-service", null,
                List.of(new SimpleGrantedAuthority("SCHEDULE_PAYMENT"))));
        try {
            SecurityContextHolder.setContext(internal);
            return confirmation.confirm(payment.getParticipantId(), reference, payment.getId()).name();
        } finally { SecurityContextHolder.setContext(previous); }
    }
}
