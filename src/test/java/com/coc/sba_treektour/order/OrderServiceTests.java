package com.coc.sba_treektour.order;

import com.coc.sba_treektour.modules.order.dto.requset.CreateOrderItemRequest;
import com.coc.sba_treektour.modules.order.dto.requset.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.exception.InvalidOrderException;
import com.coc.sba_treektour.modules.order.service.*;
import com.coc.sba_treektour.modules.order.service.impl.OrderServiceImpl;
import com.coc.sba_treektour.modules.order.dto.*;
import com.coc.sba_treektour.modules.order.entity.*;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.order.repository.OrderPaymentRepository;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.product.entity.Product;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import com.coc.sba_treektour.modules.inventory.repository.InventoryRepository;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.inventory.dto.InventoryDtos.AvailabilityResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class OrderServiceTests {
    @Test void listUsesBatchedPaymentBalances() {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(9L);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("100000"));
        when(order.getItems()).thenReturn(List.of());
        when(orders.findByUser_IdOrderByOrderDateDesc(1L)).thenReturn(List.of(order));
        when(payments.readMany(List.of(9L))).thenReturn(Map.of(9L,
                new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ZERO, 0)));
        var result = service.findAll(1L);
        assertThat(result.getFirst().paymentStatus()).isEqualTo("PAID");
        assertThat(result.getFirst().canApprove()).isTrue();
        verify(payments).readMany(List.of(9L));
        verify(payments, never()).read(any());
    }
    final OrderRepository orders = mock(OrderRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final BranchRepository branches = mock(BranchRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final InventoryService inventory = mock(InventoryService.class);
    final InventoryRepository stocks = mock(InventoryRepository.class);
    final OrderPaymentRepository payments = mock(OrderPaymentRepository.class);
    final OrderService service = new OrderServiceImpl(orders, users, branches, products, inventory, stocks, payments);

    @org.junit.jupiter.api.BeforeEach void unpaidByDefault() {
        when(payments.read(any())).thenReturn(new OrderPaymentBalance(BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    CreateOrderRequest rental(LocalDate end) {
        User user = User.builder().id(1L).email("customer@test.local").status("ACTIVE").build();
        Branch branch = Branch.builder().id(2L).status("ACTIVE").build();
        Product product = new Product(); product.setId(3L); product.setRentalPricePerDay(new BigDecimal("100000"));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        when(products.lockById(3L)).thenReturn(Optional.of(product));
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        return new CreateOrderRequest(2L, null, OrderStatus.COMPLETED, BigDecimal.ONE,
                List.of(new CreateOrderItemRequest(3L, OrderItemType.RENTAL, 2, BigDecimal.ONE, LocalDate.now(), end, OrderItemStatus.RETURNED)));
    }

    @Test void pricesAndStatusesAreComputedByServer() {
        LocalDate start = LocalDate.now(), end = start.plusDays(2);
        var request = rental(end);
        when(inventory.availability(2L, 3L, start, end)).thenReturn(new AvailabilityResponse(2L, 3L, start, end, 10, 0, 10));
        var result = service.create(request, "customer@test.local");
        assertThat(result.totalAmount()).isEqualByComparingTo("600000");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.items().getFirst().status()).isEqualTo(OrderItemStatus.PENDING);
    }

    @Test void partialRentalDatesReturnValidationError() {
        var request = rental(null);
        assertThatThrownBy(() -> service.create(request, "customer@test.local")).isInstanceOf(InvalidOrderException.class);
        verify(orders, never()).save(any());
    }

    @Test void insufficientRentalCapacityDoesNotCreateOrder() {
        LocalDate date = LocalDate.now(); var request = rental(date);
        when(inventory.availability(2L, 3L, date, date)).thenReturn(new AvailabilityResponse(2L, 3L, date, date, 1, 0, 1));
        assertThatThrownBy(() -> service.create(request, "customer@test.local")).isInstanceOf(InvalidOrderException.class);
        verify(orders, never()).save(any());
    }

    @Test void repeatedHandOverDoesNotDeductStockTwice() {
        var request = rental(LocalDate.now());
        when(inventory.availability(any(), any(), any(), any())).thenReturn(
                new AvailabilityResponse(2L, 3L, LocalDate.now(), LocalDate.now(), 10, 0, 10));
        var captured = new java.util.concurrent.atomic.AtomicReference<Order>();
        when(orders.save(any())).thenAnswer(call -> { Order order = call.getArgument(0); captured.set(order); return order; });
        service.create(request, "customer@test.local");
        when(orders.lockById(9L)).thenAnswer(call -> Optional.of(captured.get()));
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("200000"), BigDecimal.ZERO, 0));
        service.operate(9L, "confirm");
        service.operate(9L, "hand-over");
        assertThatThrownBy(() -> service.operate(9L, "hand-over")).isInstanceOf(InvalidOrderException.class);
        verify(inventory, times(1)).handOutRental(2L, 3L, 2);
        service.operate(9L, "return");
        assertThatThrownBy(() -> service.operate(9L, "return")).isInstanceOf(InvalidOrderException.class);
        verify(inventory, times(1)).receiveRental(2L, 3L, 2);
    }

    Order pendingOrder() {
        var user = User.builder().id(1L).email("customer@test.local").status("ACTIVE").build();
        var branch = Branch.builder().id(2L).status("ACTIVE").build();
        var order = new Order(user, branch, java.time.OffsetDateTime.now(), OrderStatus.PENDING, new BigDecimal("100000"));
        when(orders.lockById(9L)).thenReturn(Optional.of(order));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return order;
    }

    @Test void unpaidAndUnderpaidOrdersCannotBeApproved() {
        var order = pendingOrder();
        assertThatThrownBy(() -> service.operate(9L, "confirm")).isInstanceOf(InvalidOrderException.class);
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("99999"), BigDecimal.ZERO, 0));
        assertThatThrownBy(() -> service.operate(9L, "confirm")).isInstanceOf(InvalidOrderException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(inventory);
    }

    @Test void paidOrderWaitsForExplicitAdminApproval() {
        var order = pendingOrder();
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ZERO, 0));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        service.operate(9L, "confirm");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(inventory);
    }

    @Test void refundedAndProcessingPaymentsBlockOrdinaryCancellation() {
        var order = pendingOrder();
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(BigDecimal.ONE, BigDecimal.ONE, 0));
        assertThatThrownBy(() -> service.cancel(9L, "customer@test.local")).isInstanceOf(InvalidOrderException.class);
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(BigDecimal.ZERO, BigDecimal.ZERO, 1));
        assertThatThrownBy(() -> service.cancel(9L, "customer@test.local")).isInstanceOf(InvalidOrderException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test void refundReducesBalanceAndBlocksApproval() {
        pendingOrder();
        var balance = new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ONE, 0);
        when(payments.read(9L)).thenReturn(balance);
        assertThat(balance.status(new BigDecimal("100000"))).isEqualTo("PARTIALLY_PAID");
        assertThatThrownBy(() -> service.operate(9L, "confirm")).isInstanceOf(InvalidOrderException.class);
    }
}
