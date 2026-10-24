package com.coc.sba_treektour.payment;

import com.coc.sba_treektour.common.config.ZaloPayConfig;
import com.coc.sba_treektour.modules.payment.dto.*;
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.TransactionsServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.entity.OrderStatus;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.account.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.springframework.http.MediaType;

class OrderPaymentTests {
    TransactionRepository repository = mock(TransactionRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    Order order = mock(Order.class);
    User owner = User.builder().id(9L).email("owner@test.local").status("ACTIVE").build();
    TransactionTemplate template = mock(TransactionTemplate.class);
    EntityManager em = mock(EntityManager.class);
    com.coc.sba_treektour.modules.payment.service.ParticipantPaymentBridge participantBridge = mock(com.coc.sba_treektour.modules.payment.service.ParticipantPaymentBridge.class);
    ZaloPayConfig config = new ZaloPayConfig();
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TransactionsServiceImpl service;
    Transaction transaction;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setup() throws Exception {
        config.setAppId(123); config.setKey1("test-key1"); config.setKey2("test-key2");
        config.setCreateUrl("https://provider.test/create"); config.setQueryUrl("https://provider.test/query");
        service = new TransactionsServiceImpl(repository, config, builder.build(), new ObjectMapper(), orders, template, em, participantBridge);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "returnUrl", "http://localhost:3000/payment/result");
        when(template.execute(any())).thenAnswer(i -> ((TransactionCallback) i.getArgument(0)).doInTransaction(null));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@test.local", "", List.of()));
        when(orders.lockById(5L)).thenReturn(Optional.of(order));
        when(order.getId()).thenReturn(5L);
        when(order.getUser()).thenReturn(owner);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("200000"));
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(repository.totalAmount(eq(5L), anyString(), eq(SUCCEEDED))).thenReturn(BigDecimal.ZERO);
        when(repository.saveAndFlush(any())).thenAnswer(i -> { Transaction t = i.getArgument(0); t.setId(11L); return t; });
        transaction = Transaction.builder().id(11L).order(order).amount(new BigDecimal("200000"))
                .createdAt(OffsetDateTime.parse("2026-10-23T10:00:00+07:00"))
                .paymentMethod("ZALOPAY").transactionType("PAYMENT").status(PENDING).build();
        when(repository.findById(11L)).thenReturn(Optional.of(transaction));
        when(repository.findByIdForUpdate(11L)).thenReturn(Optional.of(transaction));
        when(repository.findOrderIdByTransactionId(11L)).thenReturn(Optional.of(5L));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void usesServerAmountAndAllowsOmittingClientAmount() {
        server.expect(requestTo(config.getCreateUrl())).andExpect(content().string(org.hamcrest.Matchers.containsString("amount=200000")))
                .andRespond(withSuccess("{\"return_code\":1}", MediaType.APPLICATION_JSON));
        var result = service.createZaloPayPayment(new CreateZaloPayPaymentRequest(5L,null,null,null,null));
        assertEquals(PENDING,result.status());
        assertEquals(5L,result.orderId());
        assertEquals("http://localhost:3000/payment/result?transactionId=11", result.redirectUrl());
        var captor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals(new BigDecimal("200000"),captor.getValue().getAmount());
        server.verify();
    }

    @Test void ignoresClientRedirectAndUsesConfiguredReturnUrl() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "returnUrl",
                "https://frontend.test/payment/result?source=checkout");
        var request = new ObjectMapper().readValue(
                "{\"orderId\":5,\"redirectUrl\":\"https://untrusted.test\"}", CreateZaloPayPaymentRequest.class);
        server.expect(requestTo(config.getCreateUrl()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("frontend.test")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("untrusted.test"))))
                .andRespond(withSuccess("{\"return_code\":1}", MediaType.APPLICATION_JSON));
        var result = service.createZaloPayPayment(request);
        assertEquals("https://frontend.test/payment/result?source=checkout&transactionId=11", result.redirectUrl());
        server.verify();
    }

    @Test void rejectsWrongOwnerBeforeCreatingPayment() {
        owner.setEmail("other@test.local");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.createZaloPayPayment(
                new CreateZaloPayPaymentRequest(5L,null,null,null,null))).getStatusCode().value());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void rejectsDuplicatePendingOrReceivedPayments() {
        when(repository.existsByOrder_IdAndTransactionTypeAndStatus(5L, "PAYMENT", PENDING)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.createZaloPayPayment(new CreateZaloPayPaymentRequest(5L,null,null,null,null)));
        when(repository.existsByOrder_IdAndTransactionTypeAndStatus(5L, "PAYMENT", PENDING)).thenReturn(false);
        when(repository.totalAmount(5L,"PAYMENT", SUCCEEDED)).thenReturn(BigDecimal.ONE);
        assertThrows(ResponseStatusException.class, () -> service.createZaloPayPayment(new CreateZaloPayPaymentRequest(5L,null,null,null,null)));
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void rejectsCancelledOrder() throws Exception {
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        assertThrows(ResponseStatusException.class, () -> service.createZaloPayPayment(new CreateZaloPayPaymentRequest(5L,null,null,null,null)));
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void providerTimeoutKeepsPendingForReconciliation() {
        server.expect(requestTo(config.getCreateUrl())).andRespond(withException(new java.io.IOException("timeout")));
        var failure = assertThrows(ResponseStatusException.class, () -> service.createZaloPayPayment(
                new CreateZaloPayPaymentRequest(5L,null,BigDecimal.ONE,"ignored",null)));
        assertEquals(502, failure.getStatusCode().value());
        assertTrue(failure.getReason().contains("11"));
        var captor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals(PENDING,captor.getValue().getStatus());
        assertEquals(new BigDecimal("200000"),captor.getValue().getAmount());
    }

    @Test void querySuccessVerifiesAmountAndIsIdempotent() {
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        var verified = service.queryZaloPayPayment(11L);
        assertEquals(SUCCEEDED, verified.status());
        assertEquals(5L, verified.orderId());
        assertEquals(SUCCEEDED,service.queryZaloPayPayment(11L).status());
        verify(repository,times(1)).saveAndFlush(transaction);
        var locks = inOrder(orders,repository);
        locks.verify(orders).lockById(5L);
        locks.verify(repository).findByIdForUpdate(11L);
    }

    @Test void queryRejectsMismatchedAmount() {
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":1,\"amount\":1}",MediaType.APPLICATION_JSON));
        assertEquals(502,assertThrows(ResponseStatusException.class, () -> service.queryZaloPayPayment(11L)).getStatusCode().value());
        assertEquals(PENDING,transaction.getStatus());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void queryProcessingStaysPendingThenExplicitFailureBecomesFailed() {
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":2,\"is_processing\":false}",MediaType.APPLICATION_JSON));
        assertEquals(PENDING,service.queryZaloPayPayment(11L).status());
        assertEquals(FAILED,service.queryZaloPayPayment(11L).status());
    }

    @Test void createIncludesMerchantIdInFrontendRedirectAndOmitsCallbackUrl() {
        server.expect(requestTo(config.getCreateUrl()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("callback_url"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("expire_duration_seconds=900")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("transactionId")))
                .andRespond(withSuccess("{\"return_code\":1}",MediaType.APPLICATION_JSON));
        service.createZaloPayPayment(new CreateZaloPayPaymentRequest(5L,null,null,null,null));
        server.verify();
    }

    @Test void providerExpiryBecomesFailedEvenWithoutProcessingFlag() {
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":2,\"sub_return_code\":-54}",MediaType.APPLICATION_JSON));
        assertEquals(FAILED,service.queryZaloPayPayment(11L).status());
    }

    @Test void queryErrorsAndInsufficientBalanceDoNotReleasePendingPayment() {
        for (int code : new int[]{-401,-402,-429,-500,-999,-63,-101}) {
            server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                    "{\"return_code\":2,\"is_processing\":false,\"sub_return_code\":" + code + "}",MediaType.APPLICATION_JSON));
        }
        for (int i=0;i<7;i++) assertEquals(PENDING,service.queryZaloPayPayment(11L).status());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void localAgeAloneNeverTurnsProviderProcessingIntoFailed() {
        transaction.setCreatedAt(OffsetDateTime.now().minusHours(2));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        assertEquals(PENDING,service.queryZaloPayPayment(11L).status());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void recoversAndQueriesPendingPaymentByOrderWithoutBrowserStorage() {
        when(orders.findById(5L)).thenReturn(Optional.of(order));
        when(repository.findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
                5L,"ZALOPAY","PAYMENT",PENDING)).thenReturn(Optional.of(transaction));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        var result = service.queryOrderPayment(5L);
        assertEquals(11L,result.transactionId());
        assertEquals(SUCCEEDED,result.status());
        verify(repository,never()).findFirstByOrder_IdAndPaymentMethodAndTransactionTypeOrderByIdDesc(anyLong(),anyString(),anyString());
    }

    @Test void orderWithoutPaymentReturnsNullWithoutCallingProvider() {
        when(orders.findById(5L)).thenReturn(Optional.of(order));
        assertNull(service.queryOrderPayment(5L));
        server.verify();
    }

    @Test void recoveryChecksOwnerBeforeReadingTransactions() {
        when(orders.findById(5L)).thenReturn(Optional.of(order));
        owner.setEmail("other@test.local");
        assertEquals(403,assertThrows(ResponseStatusException.class, () -> service.queryOrderPayment(5L)).getStatusCode().value());
        verify(repository,never()).findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(anyLong(),anyString(),anyString(),any());
    }

    private CreateZaloPayPaymentRequest retryRequest() {
        return new CreateZaloPayPaymentRequest(5L,null,null,null,null);
    }

    private void pendingAttempt() {
        when(repository.findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
                5L,"ZALOPAY","PAYMENT",PENDING)).thenReturn(Optional.of(transaction));
    }

    @Test void retryCancelsProviderAttemptBeforeCreatingAnotherOnSameOrder() {
        pendingAttempt();
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v2/cancel")).andExpect(content().string(
                org.hamcrest.Matchers.containsString("app_trans_id=261023_TWM_11")))
                .andRespond(withSuccess("{\"return_code\":1}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.getCreateUrl())).andRespond(withSuccess(
                "{\"return_code\":1,\"order_url\":\"https://provider.test/pay/new\"}",MediaType.APPLICATION_JSON));
        var result = service.retryOrderPayment(retryRequest());
        assertEquals(PENDING,result.status());
        assertEquals(5L,result.orderId());
        assertEquals(CANCELLED,transaction.getStatus());
        var saved = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(repository,times(2)).saveAndFlush(saved.capture());
        assertEquals(order,saved.getAllValues().getLast().getOrder());
        server.verify();
    }

    @Test void retryReturnsAlreadySucceededInsteadOfChargingAgain() {
        pendingAttempt();
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        assertEquals(SUCCEEDED,service.retryOrderPayment(retryRequest()).status());
        verify(repository,times(1)).saveAndFlush(transaction);
        server.verify();
    }

    @Test void cancelTimeoutDoesNotCreateNewAttempt() {
        pendingAttempt();
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v2/cancel"))
                .andRespond(withException(new java.io.IOException("timeout")));
        assertEquals(502,assertThrows(ResponseStatusException.class,
                () -> service.retryOrderPayment(retryRequest())).getStatusCode().value());
        assertEquals(PENDING,transaction.getStatus());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void cancelRejectedRequeriesAndDoesNotCreateNewAttempt() {
        pendingAttempt();
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v2/cancel")).andRespond(withSuccess(
                "{\"return_code\":2,\"sub_return_code\":-500}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        assertEquals(409,assertThrows(ResponseStatusException.class,
                () -> service.retryOrderPayment(retryRequest())).getStatusCode().value());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void retryRejectsOtherOwnerBeforeProviderCall() {
        owner.setEmail("someone@test.local");
        assertEquals(403,assertThrows(ResponseStatusException.class,
                () -> service.retryOrderPayment(retryRequest())).getStatusCode().value());
        verify(repository,never()).saveAndFlush(any());
        server.verify();
    }

    @Test void cancelUnpaidPaymentThenRepeatedCancelIsIdempotent() {
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v2/cancel")).andRespond(withSuccess(
                "{\"return_code\":1}",MediaType.APPLICATION_JSON));
        assertEquals(CANCELLED,service.cancelZaloPayPayment(11L).status());
        assertEquals(CANCELLED,service.cancelZaloPayPayment(11L).status());
        verify(repository,times(1)).saveAndFlush(transaction);
        verify(order,never()).setStatus(any());
        server.verify();
    }

    @Test void cannotCancelAlreadyPaidPayment() {
        transaction.setStatus(SUCCEEDED);
        assertEquals(409,assertThrows(ResponseStatusException.class,
                () -> service.cancelZaloPayPayment(11L)).getStatusCode().value());
        verify(repository,never()).saveAndFlush(any());
        server.verify();
    }

    @Test void cancelDetectsPaymentSucceededAtProvider() {
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        assertEquals(SUCCEEDED,service.cancelZaloPayPayment(11L).status());
        verify(repository,times(1)).saveAndFlush(transaction);
        server.verify();
    }

    @Test void cancelRequiresOwner() {
        owner.setEmail("other@test.local");
        assertEquals(403,assertThrows(ResponseStatusException.class,
                () -> service.cancelZaloPayPayment(11L)).getStatusCode().value());
        verify(repository,never()).saveAndFlush(any());
        server.verify();
    }

    @Test void cancelTransportErrorKeepsPending() {
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess(
                "{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v2/cancel")).andRespond(withException(new java.io.IOException("timeout")));
        assertEquals(502,assertThrows(ResponseStatusException.class,
                () -> service.cancelZaloPayPayment(11L)).getStatusCode().value());
        assertEquals(PENDING,transaction.getStatus());
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void tourCreateUsesTicketPriceAndParticipantId() {
        var ticket=mock(com.coc.sba_treektour.modules.schedule.entity.EventParticipant.class);
        when(ticket.getId()).thenReturn(7L);
        when(ticket.getRegisteredPrice()).thenReturn(new BigDecimal("450000"));
        when(participantBridge.prepare(7L)).thenReturn(ticket);
        when(participantBridge.lock(7L)).thenReturn(ticket);
        when(participantBridge.lifetimeSeconds(ticket)).thenReturn(600);
        server.expect(requestTo(config.getCreateUrl()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("amount=450000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("expire_duration_seconds=600")))
                .andRespond(withSuccess("{\"return_code\":1}",MediaType.APPLICATION_JSON));
        service.createZaloPayPayment(new CreateZaloPayPaymentRequest(null,7L,BigDecimal.ONE,"ignored",null));
        var saved=org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(ticket,saved.getValue().getParticipant());
    }

    private void tourTransaction() {
        var ticket=mock(com.coc.sba_treektour.modules.schedule.entity.EventParticipant.class);
        when(ticket.getId()).thenReturn(7L);
        transaction.setOrder(null); transaction.setParticipant(ticket);
        when(repository.findOrderIdByTransactionId(11L)).thenReturn(Optional.empty());
        when(repository.findParticipantIdByTransactionId(11L)).thenReturn(Optional.of(7L));
    }

    @Test void successfulQueryConfirmsTourAfterCommittingMoneyUsingIdempotentScheduleContract() {
        tourTransaction();
        when(participantBridge.confirm(transaction)).thenReturn("CONFIRMED");
        doAnswer(i->{((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>)i.getArgument(0)).accept(null);return null;})
                .when(template).executeWithoutResult(any());
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        assertEquals("CONFIRMED",service.queryZaloPayPayment(11L).participantConfirmation());
        service.queryZaloPayPayment(11L);
        var sequence=inOrder(repository,participantBridge);
        sequence.verify(repository).saveAndFlush(transaction);
        sequence.verify(participantBridge).confirm(transaction);
        verify(participantBridge,times(2)).confirm(transaction);
    }

    @Test void confirmationFailurePreservesMoneyForRetryFromTicketState() {
        tourTransaction();
        when(participantBridge.confirm(transaction)).thenThrow(new RuntimeException("temporary"));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        assertThrows(RuntimeException.class,()->service.queryZaloPayPayment(11L));
        assertEquals(SUCCEEDED,transaction.getStatus());
        verify(repository).saveAndFlush(transaction);
    }

    @Test void successfulLateTourPaymentReturnsReviewWithoutUndoingMoney() {
        tourTransaction();
        when(participantBridge.confirm(transaction)).thenReturn("REVIEW_REQUIRED");
        doAnswer(i->{((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>)i.getArgument(0)).accept(null);return null;})
                .when(template).executeWithoutResult(any());
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":1,\"amount\":200000}",MediaType.APPLICATION_JSON));
        var result=service.queryZaloPayPayment(11L);
        assertEquals(SUCCEEDED,result.status());
        assertEquals("REVIEW_REQUIRED",result.participantConfirmation());
    }

    @Test void participantQueryRecoversPaymentAndChecksOwner() {
        tourTransaction();
        var ticket=transaction.getParticipant();
        when(participantBridge.lock(7L)).thenReturn(ticket);
        when(repository.findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
                7L,"ZALOPAY","PAYMENT",PENDING)).thenReturn(Optional.of(transaction));
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":3,\"is_processing\":true}",MediaType.APPLICATION_JSON));
        assertEquals(11L,service.queryParticipantPayment(7L).transactionId());
        verify(participantBridge).requireOwner(ticket);
        verify(participantBridge,never()).confirm(any());
    }

    @Test void queryCannotDowngradeSucceededPayment() {
        transaction.setStatus(SUCCEEDED);
        server.expect(requestTo(config.getQueryUrl())).andRespond(withSuccess("{\"return_code\":2}",MediaType.APPLICATION_JSON));
        assertEquals(SUCCEEDED,service.queryZaloPayPayment(11L).status());
        verify(repository,never()).saveAndFlush(any());
    }
}
