package com.coc.sba_treektour.payment.service;

import com.coc.sba_treektour.common.config.ZaloPayConfig;
import com.coc.sba_treektour.payment.dto.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.payment.dto.ZaloPayCallbackRequest;
import com.coc.sba_treektour.payment.dto.ZaloPayCallbackResponse;
import com.coc.sba_treektour.payment.dto.ZaloPayPaymentResponse;
import com.coc.sba_treektour.payment.dto.TransactionHistoryResponse;
import com.coc.sba_treektour.payment.entity.Transaction;
import com.coc.sba_treektour.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.coc.sba_treektour.payment.enums.TransactionStatus.FAILED;
import static com.coc.sba_treektour.payment.enums.TransactionStatus.PENDING;
import static com.coc.sba_treektour.payment.enums.TransactionStatus.SUCCEEDED;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TransactionsServiceImpl implements TransactionsService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter APP_TRANS_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Pattern APP_TRANS_ID_PATTERN = Pattern.compile("^\\d{6}_TWM_(\\d+)$");
    private static final String PAYMENT_METHOD = "ZALOPAY";
    private static final String PAYMENT = "PAYMENT";
    private final TransactionRepository transactionRepository;
    private final ZaloPayConfig zaloPayConfig;
    private final RestClient zaloPayRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public ZaloPayPaymentResponse createZaloPayPayment(CreateZaloPayPaymentRequest request) {
        BigDecimal amount = request.amount();
        long amountInVnd = toVnd(amount);
        String appUser = request.appUser().trim();
        OffsetDateTime now = OffsetDateTime.now(VIETNAM_ZONE);
        Transaction transaction = transactionRepository.saveAndFlush(Transaction.builder()
                .orderId(request.orderId())
                .participantId(request.participantId())
                .transactionType(PAYMENT)
                .amount(amount)
                .paymentMethod(PAYMENT_METHOD)
                .status(PENDING)
                .createdAt(now)
                .build());

        String appTransId = buildAppTransId(transaction);
        long appTime = now.toInstant().toEpochMilli();
        String item = "[]";
        String embedData = buildEmbedData(transaction.getId(), request.redirectUrl());
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
        form.add("amount", String.valueOf(amountInVnd));
        form.add("description", description);
        form.add("callback_url", zaloPayConfig.getCallbackUrl());
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
            transaction.setStatus(FAILED);
            transactionRepository.save(transaction);
            throw new ResponseStatusException(BAD_GATEWAY, "Cannot connect to ZaloPay", exception);
        }

        if (providerResponse == null || providerResponse.path("return_code").asInt() != 1) {
            transaction.setStatus(FAILED);
            transactionRepository.save(transaction);
        }

        return new ZaloPayPaymentResponse(
                transaction.getId(), appTransId, transaction.getStatus(), providerResponse);
    }

    @Override
    public ZaloPayPaymentResponse queryZaloPayPayment(Long transactionId) {
        Transaction transaction = findZaloPayTransaction(transactionId);
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

        if (providerResponse != null && !SUCCEEDED.equals(transaction.getStatus())) {
            int returnCode = providerResponse.path("return_code").asInt();
            transaction.setStatus(switch (returnCode) {
                case 1 -> SUCCEEDED;
                case 2 -> FAILED;
                default -> PENDING;
            });
            transactionRepository.save(transaction);
        }

        return new ZaloPayPaymentResponse(
                transaction.getId(), appTransId, transaction.getStatus(), providerResponse);
    }

    @Override
    @Transactional
    public ZaloPayCallbackResponse processZaloPayCallback(ZaloPayCallbackRequest request) {
        if (request.type() != 1) {
            return new ZaloPayCallbackResponse(2, "Invalid callback type");
        }
        String expectedMac = hmacSha256(zaloPayConfig.getKey2(), request.data());
        if (!MessageDigest.isEqual(
                expectedMac.getBytes(StandardCharsets.UTF_8),
                request.mac().getBytes(StandardCharsets.UTF_8))) {
            return new ZaloPayCallbackResponse(2, "Invalid MAC");
        }

        try {
            JsonNode callbackData = objectMapper.readTree(request.data());
            if (callbackData.path("app_id").asInt() != zaloPayConfig.getAppId()) {
                return new ZaloPayCallbackResponse(2, "Invalid app_id");
            }

            String appTransId = callbackData.path("app_trans_id").asString();
            Transaction transaction = findZaloPayTransaction(extractTransactionId(appTransId));
            if (!buildAppTransId(transaction).equals(appTransId)) {
                return new ZaloPayCallbackResponse(2, "Invalid app_trans_id");
            }
            if (toVnd(transaction.getAmount()) != callbackData.path("amount").asLong()) {
                return new ZaloPayCallbackResponse(2, "Invalid amount");
            }

            transaction.setStatus(SUCCEEDED);
            transactionRepository.save(transaction);
            return new ZaloPayCallbackResponse(1, "Success");
        } catch (JacksonException | ResponseStatusException | IllegalArgumentException exception) {
            return new ZaloPayCallbackResponse(2, "Invalid callback data");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getTransactionHistory() {
        return transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toHistoryResponse)
                .toList();
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

    private String buildAppTransId(Transaction transaction) {
        String date = transaction.getCreatedAt().atZoneSameInstant(VIETNAM_ZONE).format(APP_TRANS_DATE);
        return date + "_TWM_" + transaction.getId();
    }

    private Long extractTransactionId(String appTransId) {
        Matcher matcher = APP_TRANS_ID_PATTERN.matcher(appTransId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid app_trans_id");
        }
        return Long.valueOf(matcher.group(1));
    }

    private String buildEmbedData(Long transactionId, String redirectUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchant_transaction_id", transactionId);
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            data.put("redirecturl", redirectUrl.trim());
        }
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
