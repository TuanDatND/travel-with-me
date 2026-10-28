package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.impl.ParticipantPaymentBridgeImpl;
import com.coc.sba_treektour.modules.schedule.entity.*;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.repository.*;
import com.coc.sba_treektour.modules.schedule.service.ParticipantPaymentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ParticipantPaymentBridgeTests {
    EventParticipantRepository participants=mock(EventParticipantRepository.class);
    EventScheduleRepository schedules=mock(EventScheduleRepository.class);
    UserRepository users=mock(UserRepository.class);
    TransactionRepository transactions=mock(TransactionRepository.class);
    ParticipantPaymentService confirmations=mock(ParticipantPaymentService.class);
    EntityManager em=mock(EntityManager.class);
    ParticipantPaymentBridgeImpl bridge=new ParticipantPaymentBridgeImpl(participants,schedules,users,transactions,confirmations,em);
    EventParticipant ticket=mock(EventParticipant.class);
    EventSchedule schedule=mock(EventSchedule.class);
    UUID reference=UUID.randomUUID();
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@test", "",List.of()));
        when(participants.findScheduleId(7L)).thenReturn(Optional.of(8L));
        when(schedules.findForUpdate(8L)).thenReturn(Optional.of(schedule));
        when(participants.findForUpdate(7L)).thenReturn(Optional.of(ticket));
        when(ticket.getId()).thenReturn(7L);
        when(ticket.getRegistrationReference()).thenReturn(reference);
        when(ticket.getUserId()).thenReturn(9L);
        when(ticket.getSchedule()).thenReturn(schedule);
        when(ticket.getParticipantStatus()).thenReturn(ParticipantStatus.PENDING);
        when(ticket.getExpiresAt()).thenReturn(OffsetDateTime.now().plusMinutes(12));
        when(schedule.getRegistrationClosesAt()).thenReturn(OffsetDateTime.now().plusHours(1));
        when(schedule.getStatus()).thenReturn(ScheduleStatus.OPEN);
        when(users.findByEmail("owner@test")).thenReturn(Optional.of(User.builder().id(9L).status("ACTIVE").build()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void locksScheduleBeforeParticipantAndBoundsLifetime() {
        assertSame(ticket,bridge.prepare(7L));
        var locks=inOrder(schedules,participants);
        locks.verify(schedules).findForUpdate(8L);
        locks.verify(participants).findForUpdate(7L);
        assertTrue(bridge.lifetimeSeconds(ticket)<=720);
    }
    @Test void rejectsExpiredShortHoldAndClosedLifecycle() {
        when(ticket.getExpiresAt()).thenReturn(OffsetDateTime.now().plusSeconds(299));
        assertThrows(ResponseStatusException.class,()->bridge.prepare(7L));
        when(ticket.getExpiresAt()).thenReturn(OffsetDateTime.now().plusMinutes(12));
        when(schedule.getStatus()).thenReturn(ScheduleStatus.CANCELLED);
        assertThrows(ResponseStatusException.class,()->bridge.prepare(7L));
    }
    @Test void rejectsWrongOwnerAndDuplicatePayments() {
        when(ticket.getUserId()).thenReturn(88L);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->bridge.prepare(7L)).getStatusCode().value());
        when(ticket.getUserId()).thenReturn(9L);
        when(transactions.existsByParticipant_IdAndTransactionTypeAndStatusIn(eq(7L),eq("PAYMENT"),any())).thenReturn(true);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->bridge.prepare(7L)).getStatusCode().value());
    }
    @Test void usesInternalIdentityAndRestoresCustomerContext() {
        var previous=SecurityContextHolder.getContext();
        when(confirmations.confirm(7L,reference,11L)).thenAnswer(i->{
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals("SCHEDULE_PAYMENT")));
            return ParticipantPaymentService.ConfirmationResult.CONFIRMED;
        });
        var payment=Transaction.builder().id(11L).participant(ticket).build();
        assertEquals("CONFIRMED",bridge.confirm(payment));
        assertSame(previous,SecurityContextHolder.getContext());
    }
    @Test void missingTicketReferenceRequiresReviewAndLatePaymentResultIsPropagated() {
        var legacy=Transaction.builder().id(11L).participant(ticket).build();
        when(ticket.getRegistrationReference()).thenReturn(null);
        assertEquals("REVIEW_REQUIRED",bridge.confirm(legacy));
        verifyNoInteractions(confirmations);
        when(ticket.getRegistrationReference()).thenReturn(reference);
        when(confirmations.confirm(7L,reference,11L)).thenReturn(ParticipantPaymentService.ConfirmationResult.REVIEW_REQUIRED);
        assertEquals("REVIEW_REQUIRED",bridge.confirm(legacy));
    }
    @Test void failureAlwaysRestoresCustomerIdentity() {
        var previous=SecurityContextHolder.getContext();
        when(confirmations.confirm(anyLong(),any(),anyLong())).thenThrow(new RuntimeException("db"));
        assertThrows(RuntimeException.class,()->bridge.confirm(Transaction.builder().id(11L).participant(ticket).build()));
        assertSame(previous,SecurityContextHolder.getContext());
    }
}
