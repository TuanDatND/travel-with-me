package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.service.OrderRefundService;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.payment.dto.request.RequestRefund;
import com.coc.sba_treektour.modules.payment.dto.request.ConfirmRefundRequest;
import com.coc.sba_treektour.modules.payment.dto.response.TransactionHistoryResponse;
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.ParticipantPaymentBridge;
import com.coc.sba_treektour.modules.payment.service.RefundService;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.payment.dto.response.RefundPreviewResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.PENDING;
import static com.coc.sba_treektour.modules.payment.enums.TransactionStatus.SUCCEEDED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final TransactionRepository transactions;
    private final OrderRepository orders;
    private final ParticipantPaymentBridge participants;
    private final UserRepository users;
    private final EntityManager entities;
    private final EventParticipantRepository participantRepository;
    private final OrderRefundService orderRefundService;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public TransactionHistoryResponse request(RequestRefund request) {
        User user = activeUser();
        Order order = request.orderId() == null ? null : lockOrder(request.orderId());
        EventParticipant ticket = request.participantId() == null ? null : participants.lock(request.participantId());
        Long owner = order != null ? order.getUserId() : ticket.getUserId();
        if (!user.getId().equals(owner)) throw new ResponseStatusException(FORBIDDEN, "Not your order or ticket");
        requireRefundableState(order, ticket);
        Long orderId = order == null ? null : order.getId();
        Long participantId = ticket == null ? null : ticket.getId();
        requireNoPendingPayment(orderId, participantId);
        // Target lock serializes duplicate requests and all payment/refund transitions.
        var existing = order != null
                ? transactions.findFirstByOrder_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(orderId, "MANUAL", "REFUND", PENDING)
                : transactions.findFirstByParticipant_IdAndPaymentMethodAndTransactionTypeAndStatusOrderByIdDesc(participantId, "MANUAL", "REFUND", PENDING);
        if (existing.isPresent()) return response(existing.get());
        BigDecimal balance = balance(orderId, participantId);
        if (balance.signum() <= 0) throw new ResponseStatusException(CONFLICT, "No received money remaining to refund");
        return response(transactions.saveAndFlush(Transaction.builder().order(order).participant(ticket)
                .transactionType("REFUND").paymentMethod("MANUAL").amount(balance).refundReason(request.reason().trim())
                .status(PENDING).createdAt(OffsetDateTime.now()).build()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public TransactionHistoryResponse confirm(Long refundId, ConfirmRefundRequest request) {
        User admin = activeUser();
        Transaction found = transactions.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund not found"));
        requireManualRefund(found);
        // Same lock order as Payment: target first, then transaction; never call a provider.
        Order order = found.getOrderId() == null ? null : lockOrder(found.getOrderId());
        EventParticipant ticket = found.getParticipantId() == null ? null : participants.lock(found.getParticipantId());
        Transaction refund = transactions.findByIdForUpdate(refundId).orElseThrow();
        entities.refresh(refund, LockModeType.PESSIMISTIC_WRITE);
        requireManualRefund(refund);
        if (refund.getStatus() == SUCCEEDED) return response(refund);
        if (refund.getStatus() != PENDING) throw new ResponseStatusException(CONFLICT, "Refund is not pending");
        requireRefundableState(order, ticket);
        requireNoPendingPayment(refund.getOrderId(), refund.getParticipantId());
        BigDecimal remaining = balance(refund.getOrderId(), refund.getParticipantId());
        if (refund.getAmount().signum() <= 0 || refund.getAmount().compareTo(remaining) > 0
                || (order != null && refund.getAmount().compareTo(remaining) != 0))
            throw new ResponseStatusException(CONFLICT, "Refund must match the remaining order balance and cannot exceed received money");
        // Admin attests that money was transferred manually outside this application.
        if (order != null) orderRefundService.completeRefund(order);
        refund.setRefundConfirmedBy(admin.getId());
        refund.setRefundTransferReference(request.transferReference().trim());
        refund.setRefundConfirmationNote(request.note() == null ? null : request.note().trim());
        refund.setStatus(SUCCEEDED);
        if (refund.getSucceededAt() == null) refund.setSucceededAt(OffsetDateTime.now());
        return response(transactions.saveAndFlush(refund));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public List<TransactionHistoryResponse> mine(int page, int size) {
        return transactions.findMyManualRefunds(activeUser().getId(), page(page, size)).stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<TransactionHistoryResponse> list(TransactionStatus status, int page, int size) {
        activeUser();
        if (status != PENDING && status != SUCCEEDED) throw new ResponseStatusException(BAD_REQUEST, "Refund filter must be PENDING or SUCCEEDED");
        return transactions.findByTransactionTypeAndPaymentMethodAndStatusOrderByIdDesc("REFUND", "MANUAL", status,
                page(page, size)).stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public RefundPreviewResponse preview(Long orderId, Long participantId) {
        if ((orderId == null) == (participantId == null)) throw new ResponseStatusException(BAD_REQUEST, "Provide exactly one target");
        User user = activeUser();
        Order order = orderId == null ? null : orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
        EventParticipant ticket = participantId == null ? null : participantRepository.findById(participantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
        if (!user.getId().equals(order != null ? order.getUserId() : ticket.getUserId()))
            throw new ResponseStatusException(FORBIDDEN, "Not your order or ticket");
        BigDecimal received = total(orderId, participantId, "PAYMENT");
        BigDecimal refunded = total(orderId, participantId, "REFUND");
        return new RefundPreviewResponse(orderId, participantId, received, refunded, received.subtract(refunded).max(BigDecimal.ZERO));
    }

    private User activeUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ResponseStatusException(FORBIDDEN, "Sign in required");
        return users.findByEmail(auth.getName()).filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Active account required"));
    }

    private Order lockOrder(Long id) {
        Order order = orders.lockById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
        entities.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    private void requireRefundableState(Order order, EventParticipant ticket) {
        if (order != null && order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CANCELLED)
            throw new ResponseStatusException(CONFLICT, "Contact branch to cancel/return an approved or delivered order before refund");
        if (ticket != null && ticket.getParticipantStatus() != ParticipantStatus.CANCELLED && ticket.getParticipantStatus() != ParticipantStatus.EXPIRED)
            throw new ResponseStatusException(CONFLICT, "Cancel the ticket before requesting a refund");
    }

    private BigDecimal balance(Long orderId, Long participantId) {
        return total(orderId, participantId, "PAYMENT").subtract(total(orderId, participantId, "REFUND"));
    }

    private BigDecimal total(Long orderId, Long participantId, String type) {
        return orderId != null ? transactions.totalAmount(orderId, type, SUCCEEDED)
                : transactions.participantTotal(participantId, type, SUCCEEDED);
    }

    private void requireNoPendingPayment(Long orderId, Long participantId) {
        boolean pending = orderId != null
                ? transactions.existsByOrder_IdAndTransactionTypeAndStatus(orderId, "PAYMENT", PENDING)
                : transactions.existsByParticipant_IdAndTransactionTypeAndStatusIn(participantId, "PAYMENT", List.of(PENDING));
        if (pending) throw new ResponseStatusException(CONFLICT, "Payment is still processing; reconcile it before refund");
    }

    private void requireManualRefund(Transaction transaction) {
        if (!"REFUND".equals(transaction.getTransactionType()) || !"MANUAL".equals(transaction.getPaymentMethod()))
            throw new ResponseStatusException(BAD_REQUEST, "Not a manual refund request");
    }

    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(BAD_REQUEST, "page >= 0; size 1-100");
        return PageRequest.of(page, size);
    }

    private TransactionHistoryResponse response(Transaction transaction) {
        return new TransactionHistoryResponse(transaction.getId(), transaction.getOrderId(), transaction.getParticipantId(),
                transaction.getTransactionType(), transaction.getAmount(), transaction.getPaymentMethod(),
                transaction.getStatus(), transaction.getCreatedAt(), transaction.getRefundReason(), transaction.getSucceededAt(),
                transaction.getRefundConfirmedBy(), transaction.getRefundTransferReference(), transaction.getRefundConfirmationNote());
    }
}
