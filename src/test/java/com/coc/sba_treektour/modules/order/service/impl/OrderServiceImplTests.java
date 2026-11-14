package com.coc.sba_treektour.modules.order.service.impl;

import com.coc.sba_treektour.modules.order.dto.request.CreateOrderItemRequest;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.exception.InvalidOrderException;
import com.coc.sba_treektour.modules.order.service.OrderService;
import com.coc.sba_treektour.modules.payment.dto.response.OrderPaymentBalance;
import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.entity.OrderItem;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderItemType;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.payment.service.OrderPaymentQueryService;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.product.entity.Product;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import com.coc.sba_treektour.modules.inventory.repository.InventoryRepository;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.inventory.dto.response.AvailabilityResponse;
import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.security.ScheduleAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.time.OffsetDateTime;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceImplTests {
    @Test void pendingRefundBlocksApprovalAndHandoverEvenWhenFullyPaid() {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(9L);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("100000"));
        when(order.getItems()).thenReturn(List.of());
        when(orders.lockById(9L)).thenReturn(Optional.of(order));
        when(orders.findById(9L)).thenReturn(Optional.of(order));
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ZERO, 0, 1));
        assertThat(service.findById(9L).refundPending()).isTrue();
        assertThat(service.findById(9L).canApprove()).isFalse();
        assertThatThrownBy(() -> service.operate(9L, "confirm")).isInstanceOf(InvalidOrderException.class);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.operate(9L, "hand-over")).isInstanceOf(InvalidOrderException.class);
        verify(order, never()).setStatus(any());
        verifyNoInteractions(inventory);
    }
    @Test void myOrdersUsesAuthenticatedOwnerWithoutAdministrativeBranchFilter() {
        var policy = mock(ScheduleAccessPolicy.class);
        var scoped = new OrderServiceImpl(orders, users, branches, products, inventory, stocks, payments,
                mock(EventScheduleRepository.class), policy);
        var user = User.builder().id(1L).email("customer@test.local").status("ACTIVE").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(9L);
        when(order.getUserId()).thenReturn(1L);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("100000"));
        when(order.getItems()).thenReturn(List.of());
        when(orders.findByUser_IdOrderByOrderDateDesc(1L)).thenReturn(List.of(order));
        when(payments.readMany(List.of(9L))).thenReturn(Map.of(9L,
                new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ZERO, 0)));

        var result = scoped.findMyOrders(user.getEmail());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(9L);
        assertThat(result.getFirst().paymentStatus()).isEqualTo("PAID");
        verify(orders).findByUser_IdOrderByOrderDateDesc(1L);
        verify(orders, never()).findAll();
        verifyNoInteractions(policy);
        verify(payments).readMany(List.of(9L));
        user.setStatus("BLOCKED");
        assertThatThrownBy(() -> scoped.findMyOrders(user.getEmail())).isInstanceOf(InvalidOrderException.class);
    }

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
    final OrderPaymentQueryService payments = mock(OrderPaymentQueryService.class);
    final OrderService service = new OrderServiceImpl(orders, users, branches, products, inventory, stocks, payments);

    @BeforeEach void unpaidByDefault() {
        when(payments.read(any())).thenReturn(new OrderPaymentBalance(BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    CreateOrderRequest rental(LocalDate end) {
        User user = User.builder().id(1L).email("customer@test.local").status("ACTIVE").fullName(" Nguyen Van An ").phone(" 0901234567 ").address(" 12 Nguyen Trai, HCMC ").build();
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

    @Test void outOfStockRentalCanBeOrderedWithCustomerContact() {
        var original = rental(LocalDate.now());
        var request = original;
        var result = service.create(request, "customer@test.local");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.customerName()).isEqualTo("Nguyen Van An");
        assertThat(result.customerPhone()).isEqualTo("0901234567");
        assertThat(result.customerAddress()).isEqualTo("12 Nguyen Trai, HCMC");
        assertThat(result.customerEmail()).isEqualTo("customer@test.local");
        verifyNoInteractions(inventory, stocks);
        verify(orders).save(any());
    }

    @Test void incompleteProfileCannotPlaceOrder() {
        var request = rental(LocalDate.now());
        var user = users.findByEmail("customer@test.local").orElseThrow();
        user.setAddress(null);
        assertThatThrownBy(() -> service.create(request, user.getEmail()))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("update your profile");
        user.setAddress("12 Nguyen Trai"); user.setPhone(null);
        assertThatThrownBy(() -> service.create(request, user.getEmail())).isInstanceOf(InvalidOrderException.class);
        user.setPhone("abc");
        assertThatThrownBy(() -> service.create(request, user.getEmail())).isInstanceOf(InvalidOrderException.class);
        user.setPhone("0901234567"); user.setFullName(" ");
        assertThatThrownBy(() -> service.create(request, user.getEmail())).isInstanceOf(InvalidOrderException.class);
        verify(orders, never()).save(any());
    }

    @Test void saleCanBeOrderedWithoutInventoryAtBranch() {
        rental(LocalDate.now());
        var product = products.findById(3L).orElseThrow();
        product.setSalePrice(new BigDecimal("50000"));
        var request = new CreateOrderRequest(2L, null, null, null,
                List.of(new CreateOrderItemRequest(3L, OrderItemType.SALE, 5, null, null, null, null)));
        var result = service.create(request, "customer@test.local");
        assertThat(result.totalAmount()).isEqualByComparingTo("250000");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(inventory, stocks);
    }

    @Test void repeatedHandOverDoesNotDeductStockTwice() {
        var request = rental(LocalDate.now());
        when(inventory.availability(any(), any(), any(), any())).thenReturn(
                new AvailabilityResponse(2L, 3L, LocalDate.now(), LocalDate.now(), 10, 0, 10));
        var captured = new AtomicReference<Order>();
        when(orders.save(any())).thenAnswer(call -> { Order order = call.getArgument(0); captured.set(order); return order; });
        service.create(request, "customer@test.local");
        when(orders.lockById(9L)).thenAnswer(call -> Optional.of(captured.get()));
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("200000"), BigDecimal.ZERO, 0));
        service.operate(9L, "confirm");
        service.operate(9L, "hand-over");
        assertThatThrownBy(() -> service.operate(9L, "hand-over")).isInstanceOf(InvalidOrderException.class);
        verify(inventory, times(1)).handOutRental(2L, 3L, 2);
        assertThatThrownBy(() -> service.operate(9L, "return")).isInstanceOf(InvalidOrderException.class);
        verify(inventory, never()).receiveRental(any(), any(), anyInt());
        service.confirmReceipt(9L, "customer@test.local");
        var receivedAt = captured.get().getCustomerReceivedAt();
        service.confirmReceipt(9L, "customer@test.local");
        assertThat(captured.get().getCustomerReceivedAt()).isEqualTo(receivedAt);
        service.operate(9L, "return");
        assertThatThrownBy(() -> service.operate(9L, "return")).isInstanceOf(InvalidOrderException.class);
        verify(inventory, times(1)).receiveRental(2L, 3L, 2);
    }

    Order pendingOrder() {
        var user = User.builder().id(1L).email("customer@test.local").status("ACTIVE").fullName(" Nguyen Van An ").phone(" 0901234567 ").address(" 12 Nguyen Trai, HCMC ").build();
        var branch = Branch.builder().id(2L).status("ACTIVE").build();
        var order = new Order(user, branch, OffsetDateTime.now(), OrderStatus.PENDING, new BigDecimal("100000"));
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

    @Test void customerCannotConfirmReceiptBeforeHandover() {
        var order = pendingOrder();
        assertThatThrownBy(() -> service.confirmReceipt(9L, "customer@test.local"))
                .isInstanceOf(InvalidOrderException.class);
        assertThat(order.getCustomerReceivedAt()).isNull();
        verifyNoInteractions(inventory);
    }

    @Test void anotherCustomerCannotConfirmReceipt() {
        var order = pendingOrder();
        order.setStatus(OrderStatus.IN_PROGRESS);
        when(users.findByEmail("other@test.local")).thenReturn(Optional.of(
                User.builder().id(8L).email("other@test.local").status("ACTIVE").build()));
        assertThatThrownBy(() -> service.confirmReceipt(9L, "other@test.local"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(order.getCustomerReceivedAt()).isNull();
    }

    @Test void saleCompletesOnlyAfterCustomerReceipt() {
        var order = pendingOrder();
        order.addItem(new OrderItem(3L, OrderItemType.SALE, 1, new BigDecimal("100000"), null, null, OrderItemStatus.CONFIRMED));
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ZERO, 0));
        service.operate(9L, "confirm");
        var handedOver = service.operate(9L, "hand-over");
        assertThat(handedOver.status()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(handedOver.canConfirmReceipt()).isTrue();
        assertThat(handedOver.canReturn()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(order.getCustomerReceivedAt()).isNull();
        service.confirmReceipt(9L, "customer@test.local");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(inventory, times(1)).fulfillSale(2L, 3L, 1);
        verify(inventory, never()).receiveRental(any(), any(), anyInt());
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

    @Test void tourDeliveryRequiresGuidePickupBeforeHandoverAndDeductsStockOnce() {
        var order = pendingOrder();
        order.setDeliveryInfo("TOUR_DELIVERY", 42L, "Fansipan Basecamp", "Call guide");
        order.addItem(new OrderItem(3L, OrderItemType.RENTAL, 2, new BigDecimal("100000"),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), OrderItemStatus.CONFIRMED));
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ZERO, 0));

        service.operate(9L, "confirm");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(inventory);

        assertThatThrownBy(() -> service.operate(9L, "hand-over")).isInstanceOf(InvalidOrderException.class);

        var withGuide = service.operate(9L, "guide-pickup");
        assertThat(withGuide.status()).isEqualTo(OrderStatus.WITH_GUIDE);
        assertThat(withGuide.canConfirmReceipt()).isFalse();
        verify(inventory, times(1)).handOutRental(2L, 3L, 2);

        var handedOver = service.operate(9L, "hand-over");
        assertThat(handedOver.status()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(handedOver.canConfirmReceipt()).isTrue();
        verify(inventory, times(1)).handOutRental(2L, 3L, 2);

        service.confirmReceipt(9L, "customer@test.local");
        var completed = service.operate(9L, "return");
        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED);
        verify(inventory, times(1)).receiveRental(2L, 3L, 2);
    }

    @Test void guideCannotApproveOrDispatchAndStaffCanDispatchToGuide() {
        var scheduleRepo = mock(EventScheduleRepository.class);
        var policy = mock(ScheduleAccessPolicy.class);
        var scopedService = new OrderServiceImpl(orders, users, branches, products, inventory, stocks, payments, scheduleRepo, policy);

        var order = pendingOrder();
        order.setDeliveryInfo("TOUR_DELIVERY", 42L, "Sapa Station", null);
        order.addItem(new OrderItem(3L, OrderItemType.SALE, 1, new BigDecimal("100000"), null, null, OrderItemStatus.PENDING));
        when(orders.findById(9L)).thenReturn(Optional.of(order));
        when(payments.read(9L)).thenReturn(new OrderPaymentBalance(new BigDecimal("100000"), BigDecimal.ZERO, 0));
        var schedule = mock(EventSchedule.class);
        when(scheduleRepo.findById(42L)).thenReturn(Optional.of(schedule));

        when(policy.hasRole("ADMIN")).thenReturn(false);
        when(policy.hasRole("STAFF")).thenReturn(false);
        assertThatThrownBy(() -> scopedService.operateAsActor(9L, "confirm"))
                .isInstanceOf(AccessDeniedException.class);

        when(policy.hasRole("STAFF")).thenReturn(true);
        scopedService.operateAsActor(9L, "confirm");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        when(policy.hasRole("STAFF")).thenReturn(false);
        assertThatThrownBy(() -> scopedService.operateAsActor(9L, "guide-pickup"))
                .isInstanceOf(AccessDeniedException.class);

        when(policy.hasRole("STAFF")).thenReturn(true);
        scopedService.operateAsActor(9L, "guide-pickup");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WITH_GUIDE);
        verify(inventory, times(1)).fulfillSale(2L, 3L, 1);

        when(policy.hasRole("STAFF")).thenReturn(false);
        var delivered = scopedService.operateAsActor(9L, "hand-over");
        assertThat(delivered.status()).isEqualTo(OrderStatus.IN_PROGRESS);
        verify(inventory, times(1)).fulfillSale(2L, 3L, 1);
    }

    @Test void staffCanFindOrdersForManagedBranchOnly() {
        var scheduleRepo = mock(EventScheduleRepository.class);
        var policy = mock(ScheduleAccessPolicy.class);
        var scopedService = new OrderServiceImpl(orders, users, branches, products, inventory, stocks, payments, scheduleRepo, policy);

        var order1 = pendingOrder();
        var order2 = pendingOrder();
        when(orders.findByBranch_IdOrderByOrderDateDesc(2L)).thenReturn(List.of(order1));
        when(orders.findAll()).thenReturn(List.of(order1, order2));

        when(policy.hasRole("ADMIN")).thenReturn(false);
        when(policy.hasRole("STAFF")).thenReturn(true);
        when(policy.manages(2L)).thenReturn(true);

        var result = scopedService.findAll(null, 2L);
        assertThat(result).hasSize(1);
        verify(policy).requireManager(2L);
    }
}
