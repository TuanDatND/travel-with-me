package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.payment.dto.request.RequestRefund;
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.ParticipantPaymentBridge;
import com.coc.sba_treektour.modules.payment.service.RefundService;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import com.coc.sba_treektour.modules.schedule.entity.ParticipantStatus;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.PENDING;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.SUCCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceTests {
    TransactionRepository transactions = mock(TransactionRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    ParticipantPaymentBridge participants = mock(ParticipantPaymentBridge.class);
    UserRepository users = mock(UserRepository.class);
    EntityManager entities = mock(EntityManager.class);
    RefundServiceImpl service = new RefundServiceImpl(transactions, orders, participants, users, entities);
    Order order = mock(Order.class);
    User user = User.builder().id(9L).email("owner@test").status("ACTIVE").build();

    @BeforeEach void setup() {
        login("CUSTOMER");
        when(users.findByEmail("owner@test")).thenReturn(Optional.of(user));
        when(orders.lockById(5L)).thenReturn(Optional.of(order));
        when(order.getId()).thenReturn(5L);
        when(order.getUserId()).thenReturn(9L);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(transactions.totalAmount(5L, "PAYMENT", SUCCEEDED)).thenReturn(new BigDecimal("1000"));
        when(transactions.totalAmount(5L, "REFUND", SUCCEEDED)).thenReturn(BigDecimal.ZERO);
        when(transactions.saveAndFlush(any())).thenAnswer(call -> {
            Transaction transaction = call.getArgument(0);
            if (transaction.getId() == null) transaction.setId(11L);
            return transaction;
        });
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@test", "",
                List.of(new SimpleGrantedAuthority(role))));
    }
    private Transaction refund() {
        return Transaction.builder().id(11L).order(order).transactionType("REFUND").paymentMethod("MANUAL")
                .amount(new BigDecimal("1000")).status(PENDING).build();
    }
    private void find(Transaction refund) {
        when(transactions.findById(11L)).thenReturn(Optional.of(refund));
        when(transactions.findByIdForUpdate(11L)).thenReturn(Optional.of(refund));
    }

    @Test void requestUsesServerBalanceAndDoesNotReturnMoneyYet() {
        when(transactions.totalAmount(5L, "REFUND", SUCCEEDED)).thenReturn(new BigDecimal("200"));
        var response = service.request(new RequestRefund(5L, null));
        assertEquals(new BigDecimal("800"), response.amount());
        assertEquals(PENDING, response.status());
        assertEquals("MANUAL", response.paymentMethod());
        assertEquals("REFUND", response.transactionType());
        verify(order, never()).setStatus(any());
    }
    @Test void duplicateRequestReturnsSamePendingRefund() {
        var refund = refund();
        when(transactions.findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(5L, "MANUAL", "REFUND", PENDING))
                .thenReturn(Optional.of(refund));
        assertEquals(11L, service.request(new RequestRefund(5L, null)).transactionId());
        verify(transactions, never()).saveAndFlush(any());
    }
    @Test void wrongOwnerAndBlockedAccountCannotRequest() {
        when(order.getUserId()).thenReturn(10L);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.request(new RequestRefund(5L, null))).getStatusCode().value());
        user.setStatus("BLOCKED");
        assertThrows(ResponseStatusException.class, () -> service.request(new RequestRefund(5L, null)));
        verify(transactions, never()).saveAndFlush(any());
    }
    @Test void pendingPaymentOrZeroBalanceBlocksRequest() {
        when(transactions.existsByOrder_IdAndTransactionTypeAndStatus(5L, "PAYMENT", PENDING)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.request(new RequestRefund(5L, null)));
        when(transactions.existsByOrder_IdAndTransactionTypeAndStatus(5L, "PAYMENT", PENDING)).thenReturn(false);
        when(transactions.totalAmount(5L, "REFUND", SUCCEEDED)).thenReturn(new BigDecimal("1000"));
        assertThrows(ResponseStatusException.class, () -> service.request(new RequestRefund(5L, null)));
        verify(transactions, never()).saveAndFlush(any());
    }
    @Test void approvedOrDeliveredOrderRequiresSeparateReturnFlow() {
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        assertThrows(ResponseStatusException.class, () -> service.request(new RequestRefund(5L, null)));
        verify(transactions, never()).saveAndFlush(any());
    }
    @Test void cancelledTicketCanRequestAndActiveTicketCannot() {
        var ticket = mock(EventParticipant.class);
        when(participants.lock(7L)).thenReturn(ticket);
        when(ticket.getId()).thenReturn(7L);
        when(ticket.getUserId()).thenReturn(9L);
        when(ticket.getParticipantStatus()).thenReturn(ParticipantStatus.CONFIRMED);
        assertThrows(ResponseStatusException.class, () -> service.request(new RequestRefund(null, 7L)));
        when(ticket.getParticipantStatus()).thenReturn(ParticipantStatus.CANCELLED);
        when(transactions.participantTotal(7L, "PAYMENT", SUCCEEDED)).thenReturn(BigDecimal.TEN);
        when(transactions.participantTotal(7L, "REFUND", SUCCEEDED)).thenReturn(BigDecimal.ZERO);
        assertEquals(7L, service.request(new RequestRefund(null, 7L)).participantId());
    }
    @Test void confirmationIsIdempotentAndKeepsOriginalPaymentSeparate() {
        login("ADMIN");
        var refund = refund(); find(refund);
        assertEquals(SUCCEEDED, service.confirm(11L).status());
        assertEquals(SUCCEEDED, service.confirm(11L).status());
        verify(transactions, times(1)).saveAndFlush(refund);
        var locks = inOrder(orders, transactions);
        locks.verify(orders).lockById(5L);
        locks.verify(transactions).findByIdForUpdate(11L);
        verify(order, never()).setStatus(any());
    }
    @Test void staleBalanceAndChangedOrderStateBlockConfirmation() {
        var refund = refund(); find(refund);
        when(transactions.totalAmount(5L, "REFUND", SUCCEEDED)).thenReturn(BigDecimal.ONE);
        assertThrows(ResponseStatusException.class, () -> service.confirm(11L));
        when(order.getStatus()).thenReturn(OrderStatus.IN_PROGRESS);
        assertThrows(ResponseStatusException.class, () -> service.confirm(11L));
        assertEquals(PENDING, refund.getStatus());
    }
    @Test void cannotConfirmPaymentAsRefund() {
        var refund = refund(); refund.setTransactionType("PAYMENT"); find(refund);
        assertThrows(ResponseStatusException.class, () -> service.confirm(11L));
        verify(transactions, never()).saveAndFlush(any());
    }
    @Configuration
    @EnableMethodSecurity
    static class Security {}
    @Test void actualMethodSecurityRejectsCustomerConfirmAndStaffAccess() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Security.class);
            context.registerBean(RefundService.class, () -> service);
            context.refresh();
            var secured = context.getBean(RefundService.class);
            assertThrows(AccessDeniedException.class, () -> secured.confirm(11L));
            assertThrows(AccessDeniedException.class, () -> secured.pending(0, 20));
            login("STAFF");
            assertThrows(AccessDeniedException.class, () -> secured.request(new RequestRefund(5L, null)));
            assertThrows(AccessDeniedException.class, () -> secured.mine(0, 20));
            login("ADMIN");
            var refund = refund(); find(refund);
            assertEquals(SUCCEEDED, secured.confirm(11L).status());
        }
    }
    @Test void dtoRejectsBothTargetsMissingTargetsAndNonpositiveIds() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new RequestRefund(null, null)).isEmpty());
            assertFalse(validator.validate(new RequestRefund(5L, 7L)).isEmpty());
            assertFalse(validator.validate(new RequestRefund(-1L, null)).isEmpty());
        }
    }
}
