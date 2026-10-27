package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.common.config.ZaloPayConfig;
import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.payment.dto.request.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.modules.payment.dto.response.TransactionHistoryPageResponse;
import com.coc.sba_treektour.modules.payment.dto.response.TransactionHistoryResponse;
import com.coc.sba_treektour.modules.payment.dto.response.ZaloPayPaymentResponse;
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.ParticipantPaymentBridge;
import com.coc.sba_treektour.modules.payment.service.PaymentReconciliationService;
import com.coc.sba_treektour.modules.payment.service.TransactionsService;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import com.coc.sba_treektour.modules.schedule.entity.ParticipantStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.FAILED;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.PENDING;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.SUCCEEDED;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class TransactionsServiceImpl implements TransactionsService, PaymentReconciliationService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter APP_TRANS_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String PAYMENT_METHOD = "ZALOPAY";
    private static final String PAYMENT = "PAYMENT";
    private static final int PAYMENT_LIFETIME_SECONDS = 900;
    @Value("${payment.return-url}")
    private String returnUrl;

    private final TransactionRepository transactionRepository;
    private final ZaloPayConfig zaloPayConfig;
    private final RestClient zaloPayRestClient;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final TransactionTemplate paymentTransaction;
    private final EntityManager entityManager;
    private final ParticipantPaymentBridge participantPayments;

    @Override
    public ZaloPayPaymentResponse createZaloPayPayment(CreateZaloPayPaymentRequest request) {
        if (!request.isTargetValid()) throw new ResponseStatusException(BAD_REQUEST, "Provide exactly one target");
        int[] paymentLifetime = { PAYMENT_LIFETIME_SECONDS };
        Transaction transaction = paymentTransaction.execute(tx -> {
            BigDecimal amount;
            Order order = null;
            EventParticipant participant = null;
            if (request.orderId() != null) {
                order = lockPendingOrder(request.orderId());
                amount = order.getTotalAmount();
                boolean pending = transactionRepository.existsByOrder_IdAndTransactionTypeAndStatus(order.getId(), PAYMENT, PENDING);
                BigDecimal paid = transactionRepository.totalAmount(order.getId(), PAYMENT, SUCCEEDED)
                        .subtract(transactionRepository.totalAmount(order.getId(), "REFUND", SUCCEEDED));
                if (pending) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a pending payment; query it first");
                if (paid.signum() > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has received payment; reconcile before retrying");
            }
            else {
                participant = participantPayments.prepare(request.participantId());
                amount = participant.getRegisteredPrice();
                paymentLifetime[0] = participantPayments.lifetimeSeconds(participant);
            }
            toVnd(amount);
            return transactionRepository.saveAndFlush(Transaction.builder()
                    .order(order).participant(participant)
                    .transactionType(PAYMENT).amount(amount).paymentMethod(PAYMENT_METHOD)
                    .status(PENDING).createdAt(OffsetDateTime.now(VIETNAM_ZONE)).build());
        });
        long amountInVnd = toVnd(transaction.getAmount());
        String appUser = request.orderId() != null ? "order_" + request.orderId() : "participant_" + request.participantId();
        String appTransId = buildAppTransId(transaction);
        long appTime = transaction.getCreatedAt().toInstant().toEpochMilli();
        String item = "[]";
        String embedData = buildEmbedData(transaction.getId());
        String description = request.description() == null || request.description().isBlank()
                ? "Thanh toan Travel With Me #" + transaction.getId()
                : request.description().trim();
        String macInput = String.join("|",
                String.valueOf(zaloPayConfig.getAppId()), appTransId, appUser,
                String.valueOf(amountInVnd), String.valueOf(appTime), embedData, item);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", String.valueOf(zaloPayConfig.getAppId()));
        form.add("app_user", appUser);
        form.add("app_trans_id", appTransId);
        form.add("app_time", String.valueOf(appTime));
        form.add("expire_duration_seconds", String.valueOf(paymentLifetime[0]));
        form.add("amount", String.valueOf(amountInVnd));
        form.add("description", description);
        form.add("item", item);
        form.add("embed_data", embedData);
        form.add("bank_code", "");
        form.add("mac", hmacSha256(zaloPayConfig.getKey1(), macInput));

        JsonNode providerResponse;
        try {
            providerResponse = zaloPayRestClient.post()
                    .uri(zaloPayConfig.getCreateUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException exception) {
            // Timeout is ambiguous: keep PENDING for reconciliation instead of allowing duplicate payment.
            throw new ResponseStatusException(BAD_GATEWAY, "Cannot connect to ZaloPay; query transaction " + transaction.getId() + " before retrying", exception);
        }

        if (providerResponse != null && providerResponse.path("return_code").asInt() != 1) {
            transaction = updatePaymentStatus(transaction.getId(), FAILED);
        }

        return paymentResponse(transaction, providerResponse);
    }

    @Override
    public ZaloPayPaymentResponse queryZaloPayPayment(Long transactionId) {
        Transaction transaction = paymentTransaction.execute(tx -> {
            Transaction found = findZaloPayTransaction(transactionId);
            if (found.getOrder() != null) requireOrderOwner(found.getOrder());
            if (found.getParticipant() != null) participantPayments.requireOwner(found.getParticipant());
            return found;
        });
        return queryProvider(transaction);
    }

    @Override
    public ZaloPayPaymentResponse queryOrderPayment(Long orderId) {
        Transaction transaction = paymentTransaction.execute(tx -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
            requireOrderOwner(order);
            // A previous pending attempt must be reconciled even if a later attempt exists.
            return transactionRepository.findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
                            orderId, PAYMENT_METHOD, PAYMENT, PENDING)
                    .or(() -> transactionRepository.findFirstByOrder_IdAndPaymentMethodAndTransactionTypeOrderByIdDesc(
                            orderId, PAYMENT_METHOD, PAYMENT))
                    .orElse(null);
        });
        if (transaction == null) return null;
        if (transaction.getStatus() == PENDING) return queryProvider(transaction);
        return paymentResponse(transaction, null);
    }

    @Override
    public ZaloPayPaymentResponse queryParticipantPayment(Long participantId) {
        Transaction transaction = paymentTransaction.execute(tx -> {
            var ticket = participantPayments.lock(participantId);
            participantPayments.requireOwner(ticket);
            return transactionRepository.findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
                    participantId, PAYMENT_METHOD, PAYMENT, PENDING)
                    .or(() -> transactionRepository.findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeOrderByIdDesc(
                            participantId, PAYMENT_METHOD, PAYMENT)).orElse(null);
        });
        if (transaction == null) return null;
        if (transaction.getStatus() == PENDING) return queryProvider(transaction);
        return paymentResponse(transaction, null);
    }

    @Override
    public ZaloPayPaymentResponse retryOrderPayment(CreateZaloPayPaymentRequest request) {
        if (!request.isTargetValid()) throw new ResponseStatusException(BAD_REQUEST, "Provide exactly one target");
        Transaction pending = paymentTransaction.execute(tx -> {
            if (request.participantId() != null) {
                var ticket = participantPayments.lock(request.participantId());
                participantPayments.requireOwner(ticket);
                if (ticket.getParticipantStatus() != ParticipantStatus.PENDING)
                    throw new ResponseStatusException(BAD_REQUEST, "Ticket is not PENDING");
                participantPayments.lifetimeSeconds(ticket);
                return transactionRepository.findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
                        ticket.getId(), PAYMENT_METHOD, PAYMENT, PENDING).orElse(null);
            }
            Order order = lockPendingOrder(request.orderId());
            return transactionRepository.findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(
                    order.getId(), PAYMENT_METHOD, PAYMENT, PENDING).orElse(null);
        });
        if (pending != null) {
            ZaloPayPaymentResponse result = cancelUnpaidAttempt(pending);
            if (result.status() == SUCCEEDED) return result;
        }
        // Rechecks owner, Order PENDING, pending attempts and paid balance under the same Order lock.
        return createZaloPayPayment(request);
    }

    @Override
    public ZaloPayPaymentResponse cancelZaloPayPayment(Long transactionId) {
        Transaction transaction = paymentTransaction.execute(tx -> {
            Transaction found = findZaloPayTransaction(transactionId);
            if (found.getOrder() != null) requireOrderOwner(found.getOrder());
            if (found.getParticipant() != null) participantPayments.requireOwner(found.getParticipant());
            return found;
        });
        if (transaction.getStatus() == SUCCEEDED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Giao dịch đã thanh toán thành công, không thể hủy. Cần sử dụng hoàn tiền.");
        return cancelUnpaidAttempt(transaction);
    }

    private ZaloPayPaymentResponse cancelUnpaidAttempt(Transaction transaction) {
        if (transaction.getStatus() == FAILED || transaction.getStatus() == TransactionStatus.CANCELLED)
            return paymentResponse(transaction, null);
        // Query before cancellation: money may have arrived since the browser was closed.
        ZaloPayPaymentResponse result = queryProvider(transaction);
        if (result.status() != PENDING) return result;
        String appTransId = buildAppTransId(transaction);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", String.valueOf(zaloPayConfig.getAppId()));
        form.add("app_trans_id", appTransId);
        form.add("mac", hmacSha256(zaloPayConfig.getKey1(),
                zaloPayConfig.getAppId() + "|" + appTransId + "|" + zaloPayConfig.getKey1()));
        JsonNode cancelled;
        try {
            String cancelUrl = UriComponentsBuilder.fromUriString(zaloPayConfig.getQueryUrl())
                    .replacePath("/v2/cancel").replaceQuery(null).build().toUriString();
            cancelled = zaloPayRestClient.post().uri(cancelUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Chưa xác nhận được hủy giao dịch. Vui lòng kiểm tra lại.", exception);
        }
        if (cancelled == null || cancelled.path("return_code").asInt() != 1) {
            result = queryProvider(transaction);
            if (result.status() != PENDING) return result;
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ZaloPay chưa xác nhận hủy giao dịch. Vui lòng kiểm tra lại sau.");
        }
        // Same locked transition as query/retry; concurrent success must not be overwritten.
        return paymentResponse(updatePaymentStatus(transaction.getId(), TransactionStatus.CANCELLED), cancelled);
    }

    private ZaloPayPaymentResponse paymentResponse(Transaction transaction, JsonNode providerResponse) {
        return new ZaloPayPaymentResponse(transaction.getId(), buildAppTransId(transaction),
                transaction.getStatus(), transaction.getOrderId(), transaction.getParticipantId(), confirmParticipant(transaction), buildReturnUrl(transaction.getId()), providerResponse);
    }

    // Internal reconciliation has no browser/JWT dependency; it never accepts user-supplied outcomes.
    @Override
    public void reconcilePendingPayment(Long transactionId) {
        Transaction transaction = paymentTransaction.execute(tx -> findZaloPayTransaction(transactionId));
        if (transaction.getStatus() == PENDING) queryProvider(transaction);
        else if (transaction.getStatus() == SUCCEEDED) confirmParticipant(transaction);
    }

    private String confirmParticipant(Transaction transaction) {
        if (transaction.getParticipantId() == null || transaction.getStatus() != SUCCEEDED) return null;
        // Module 5 reads the ticket and review records and makes repeated confirmation idempotent.
        // SUCCEEDED is committed first; the worker finds missing confirmation from those records.
        return participantPayments.confirm(transaction);
    }

    private ZaloPayPaymentResponse queryProvider(Transaction transaction) {
        Long transactionId = transaction.getId();
        String appTransId = buildAppTransId(transaction);
        String macInput = zaloPayConfig.getAppId() + "|" + appTransId + "|" + zaloPayConfig.getKey1();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", String.valueOf(zaloPayConfig.getAppId()));
        form.add("app_trans_id", appTransId);
        form.add("mac", hmacSha256(zaloPayConfig.getKey1(), macInput));

        JsonNode providerResponse;
        try {
            providerResponse = zaloPayRestClient.post()
                    .uri(zaloPayConfig.getQueryUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Cannot connect to ZaloPay", exception);
        }

        if (providerResponse != null) {
            int returnCode = providerResponse.path("return_code").asInt();
            if (returnCode == 1) {
                if (!providerResponse.hasNonNull("amount")
                        || toVnd(transaction.getAmount()) != toVnd(new BigDecimal(providerResponse.path("amount").asString()))) {
                    throw new ResponseStatusException(BAD_GATEWAY, "ZaloPay query amount does not match transaction");
                }
                transaction = updatePaymentStatus(transactionId, SUCCEEDED);
            } else if (returnCode == 2) {
                int subCode = providerResponse.path("sub_return_code").asInt();
                if (subCode == -54) {
                    // TIME_INVALID: provider confirms that the payment window has expired.
                    transaction = updatePaymentStatus(transactionId, FAILED);
                } else if (subCode == 0 && providerResponse.path("is_processing").isBoolean()
                        && !providerResponse.path("is_processing").asBoolean()) {
                    transaction = updatePaymentStatus(transactionId, FAILED);
                }
                // -63 can be retried by topping up; -401/-402/-429/-500/-999 are query errors.
                // Other nonzero subcodes need reconciliation, not an inferred failed payment.
            }
            // Processing or incomplete responses stay PENDING; a browser redirect never changes status.
        }

        return paymentResponse(transaction, providerResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionHistoryPageResponse getTransactionHistory(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(BAD_REQUEST, "page >= 0; size 1–100");
        var result = transactionRepository.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(this::toHistoryResponse);
        return new TransactionHistoryPageResponse(result.getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private TransactionHistoryResponse toHistoryResponse(Transaction transaction) {
        return new TransactionHistoryResponse(
                transaction.getId(),
                transaction.getOrderId(),
                transaction.getParticipantId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getPaymentMethod(),
                transaction.getStatus(),
                transaction.getCreatedAt());
    }

    private Transaction findZaloPayTransaction(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
        if (!PAYMENT_METHOD.equals(transaction.getPaymentMethod())
                || !PAYMENT.equals(transaction.getTransactionType())) {
            throw new ResponseStatusException(BAD_REQUEST, "Transaction is not a ZaloPay payment");
        }
        return transaction;
    }

    private String authenticatedEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return authentication.getName();
    }

    private void requireOrderOwner(Order order) {
        var owner = order.getUser();
        if (!authenticatedEmail().equals(owner.getEmail()) || !"ACTIVE".equals(owner.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this order");
    }

    private Order lockPendingOrder(Long orderId) {
        Order order = orderRepository.lockById(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        requireOrderOwner(order);
        if (order.getStatus() != OrderStatus.PENDING)
            throw new ResponseStatusException(BAD_REQUEST, "Only PENDING orders can be paid");
        return order;
    }

    private Transaction updatePaymentStatus(Long transactionId, TransactionStatus status) {
        return paymentTransaction.execute(tx -> {
            // Lock order before transaction, matching Order's cancellation/approval lock.
            transactionRepository.findOrderIdByTransactionId(transactionId).ifPresent(orderId ->
                    orderRepository.lockById(orderId)
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found")));
            transactionRepository.findParticipantIdByTransactionId(transactionId).ifPresent(id -> participantPayments.lock(id));
            Transaction current = transactionRepository.findByIdForUpdate(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
            // Refresh even with OpenEntityManagerInView: an earlier query may have cached this row.
            entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
            if (!PAYMENT.equals(current.getTransactionType()) || !PAYMENT_METHOD.equals(current.getPaymentMethod()))
                throw new ResponseStatusException(BAD_REQUEST, "Not a ZaloPay payment");
            if (current.getStatus() != SUCCEEDED && current.getStatus() != TransactionStatus.CANCELLED) {
                current.setStatus(status);
                transactionRepository.saveAndFlush(current);
            }
            return current;
        });
    }

    private String buildAppTransId(Transaction transaction) {
        String date = transaction.getCreatedAt().atZoneSameInstant(VIETNAM_ZONE).format(APP_TRANS_DATE);
        return date + "_TWM_" + transaction.getId();
    }

    private String buildReturnUrl(Long transactionId) {
        return UriComponentsBuilder.fromUriString(returnUrl)
                .replaceQueryParam("transactionId", transactionId).build().toUriString();
    }

    private String buildEmbedData(Long transactionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchant_transaction_id", transactionId);
        data.put("redirecturl", buildReturnUrl(transactionId));
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize ZaloPay embed_data", exception);
        }
    }

    private long toVnd(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Payment amount must be greater than zero");
        }
        try {
            return amount.longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "ZaloPay amount must be a whole VND value", exception);
        }
    }

    private String hmacSha256(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Cannot calculate ZaloPay MAC", exception);
        }
    }
}
