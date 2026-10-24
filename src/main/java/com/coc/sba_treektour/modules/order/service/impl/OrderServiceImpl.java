package com.coc.sba_treektour.modules.order.service.impl;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.inventory.repository.InventoryRepository;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderItemRequest;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.dto.response.OrderItemResponse;
import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;
import com.coc.sba_treektour.modules.order.entity.Order;
import com.coc.sba_treektour.modules.order.entity.OrderItem;
import com.coc.sba_treektour.modules.order.enums.OrderItemStatus;
import com.coc.sba_treektour.modules.order.enums.OrderItemType;
import com.coc.sba_treektour.modules.order.enums.OrderStatus;
import com.coc.sba_treektour.modules.order.exception.InvalidOrderException;
import com.coc.sba_treektour.modules.order.exception.OrderNotFoundException;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.order.service.OrderService;
import com.coc.sba_treektour.modules.payment.dto.OrderPaymentBalance;
import com.coc.sba_treektour.modules.payment.service.OrderPaymentQueryService;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final OrderPaymentQueryService orderPaymentQueryService;

    @Override
    @Transactional
    public OrderResponse create(CreateOrderRequest request, String email) {
        User user = activeUser(email);
        Branch branch = activeBranch(request.branchId());
        Order order = new Order(user, branch,
                OffsetDateTime.now(), OrderStatus.PENDING, BigDecimal.ZERO);
        // Lock products in ID order to serialize reservations and stock changes.
        request.items().stream().map(CreateOrderItemRequest::productId).distinct().sorted()
                .forEach(id -> productRepository.lockById(id).orElseThrow(() -> new InvalidOrderException("Product not found: " + id)));
        if (request.items().stream().map(CreateOrderItemRequest::productId).distinct().count() != request.items().size())
            throw new InvalidOrderException("Use one line per product in each order");
        for (var line : request.items()) {
            OrderItem item = toEntity(line);
            if (item.getItemType() == OrderItemType.RENTAL) {
                if (inventoryService.availability(branch.getId(), item.getProductId(), item.getRentalStartDate(), item.getRentalEndDate()).quantityRentable() < item.getQuantity())
                    throw new InvalidOrderException("Not enough rental capacity");
            } else {
                var stock = inventoryRepository.lock(branch.getId(), item.getProductId())
                        .orElseThrow(() -> new InvalidOrderException("No stock at selected branch"));
                if (!"ACTIVE".equals(stock.getStatus()) || stock.getQuantityAvailable() < item.getQuantity())
                    throw new InvalidOrderException("Not enough stock");
            }
            order.addItem(item);
        }
        var total = order.getItems().stream().map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
                        .multiply(BigDecimal.valueOf(i.getItemType() == OrderItemType.RENTAL
                                ? ChronoUnit.DAYS.between(i.getRentalStartDate(), i.getRentalEndDate()) + 1 : 1)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.update(branch, order.getOrderDate(), OrderStatus.PENDING, total);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findAll(Long userId) {
        List<Order> orders = userId == null ? orderRepository.findAll() : orderRepository.findByUser_IdOrderByOrderDateDesc(userId);
        var balances = orderPaymentQueryService.readMany(orders.stream().map(Order::getId).toList());
        var unpaid = OrderPaymentBalance.EMPTY;
        return orders.stream().map(order -> toResponse(order, balances.getOrDefault(order.getId(), unpaid))).toList();
    }

    @Override
    @Transactional
    public OrderResponse update(Long id, CreateOrderRequest request, String email) {
        throw new InvalidOrderException("Cancel the pending order and create a new order to change its items");
    }

    @Override
    @Transactional
    public void delete(Long id, String email) {
        cancel(id, email);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findMyOrder(Long id, String email) {
        Order order = findEntity(id);
        requireOwner(order, email);
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findMyOrders(String email) {
        return findAll(activeUser(email).getId());
    }

    private Order findEntity(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private User activeUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidOrderException("Authenticated user no longer exists"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new InvalidOrderException("Only active users can create or manage orders");
        }
        return user;
    }

    private Branch activeBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new InvalidOrderException("Branch not found: " + branchId));
        if (!"ACTIVE".equals(branch.getStatus())) {
            throw new InvalidOrderException("Branch is inactive: " + branchId);
        }
        return branch;
    }

    private void requireOwner(Order order, String email) {
        User actor = activeUser(email);
        if (!order.getUserId().equals(actor.getId())) {
            throw new AccessDeniedException("You do not own this order");
        }
    }

    private OrderItem toEntity(CreateOrderItemRequest request) {
        validateRentalDates(request);
        var product = productRepository.findById(request.productId()).filter(p -> "ACTIVE".equals(p.getStatus()))
                .orElseThrow(() -> new InvalidOrderException("Active product not found"));
        var price = request.itemType() == OrderItemType.SALE ? product.getSalePrice() : product.getRentalPricePerDay();
        if (price == null) throw new InvalidOrderException("Product does not support selected mode");
        return new OrderItem(request.productId(), request.itemType(), request.quantity(), price,
                request.rentalStartDate(), request.rentalEndDate(),
                OrderItemStatus.PENDING);
    }

    private void validateRentalDates(CreateOrderItemRequest request) {
        boolean hasDates = request.rentalStartDate() != null || request.rentalEndDate() != null;
        if (request.itemType() == OrderItemType.RENTAL && (request.rentalStartDate() == null || request.rentalEndDate() == null || request.rentalEndDate().isBefore(request.rentalStartDate()))) {
            throw new InvalidOrderException("Rental items require valid start and end dates");
        }
        if (request.itemType() == OrderItemType.SALE && hasDates) {
            throw new InvalidOrderException("Sale items must not include rental dates");
        }
    }

    private OrderResponse toResponse(Order order) {
        return toResponse(order, orderPaymentQueryService.read(order.getId()));
    }

    private OrderResponse toResponse(Order order, OrderPaymentBalance balance) {
        boolean pending = order.getStatus() == OrderStatus.PENDING;
        return new OrderResponse(order.getId(), order.getUserId(), order.getBranchId(), order.getStatus(),
                order.getTotalAmount(), order.getOrderDate(), order.getItems().stream().map(item -> new OrderItemResponse(
                item.getId(), item.getProductId(), item.getItemType(), item.getQuantity(), item.getUnitPrice(),
                item.getRentalStartDate(), item.getRentalEndDate(), item.getStatus())).toList(),
                balance.status(order.getTotalAmount()), balance.net(),
                order.getTotalAmount().subtract(balance.net()).max(BigDecimal.ZERO),
                pending && balance.paid(order.getTotalAmount()), pending && balance.cancellable(),
                order.getCustomerReceivedAt(),
                order.getStatus() == OrderStatus.IN_PROGRESS && order.getCustomerReceivedAt() == null,
                order.getStatus() == OrderStatus.IN_PROGRESS && order.getCustomerReceivedAt() != null
                        && order.getItems().stream().anyMatch(item -> item.getStatus() == OrderItemStatus.RENTED));
    }

    @Override
    @Transactional
    public OrderResponse cancel(Long id, String email) {
        Order order = orderRepository.lockById(id).orElseThrow(() -> new OrderNotFoundException(id));
        requireOwner(order, email);
        if (order.getStatus() != OrderStatus.PENDING)
            throw new InvalidOrderException("Only pending orders can be cancelled");
        if (!orderPaymentQueryService.read(id).cancellable())
            throw new InvalidOrderException("Payment received or processing; cancellation requires the refund workflow");
        order.setStatus(OrderStatus.CANCELLED);
        order.getItems().forEach(i -> i.setStatus(OrderItemStatus.CANCELLED));
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse confirmReceipt(Long id, String email) {
        Order order = orderRepository.lockById(id).orElseThrow(() -> new OrderNotFoundException(id));
        requireOwner(order, email);
        if (order.getCustomerReceivedAt() != null) return toResponse(order);
        if (order.getStatus() != OrderStatus.IN_PROGRESS)
            throw new InvalidOrderException("Receipt can only be confirmed after equipment handover");
        order.confirmCustomerReceipt();
        if (order.getItems().stream().noneMatch(item -> item.getItemType() == OrderItemType.RENTAL))
            order.setStatus(OrderStatus.COMPLETED);
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse operate(Long id, String action) {
        Order order = orderRepository.lockById(id).orElseThrow(() -> new OrderNotFoundException(id));
        OrderStatus expected = switch (action) {
            case "confirm" -> OrderStatus.PENDING;
            case "hand-over" -> OrderStatus.CONFIRMED;
            case "return" -> OrderStatus.IN_PROGRESS;
            default -> throw new InvalidOrderException("Invalid action");
        };
        if (order.getStatus() != expected) throw new InvalidOrderException("Order must be " + expected);
        var items = order.getItems().stream().sorted(Comparator.comparing(OrderItem::getProductId)).toList();
        if (action.equals("confirm")) {
            if (!orderPaymentQueryService.read(id).paid(order.getTotalAmount()))
                throw new InvalidOrderException("Order must be fully paid before admin approval");
            order.setStatus(OrderStatus.CONFIRMED);
            items.forEach(i -> i.setStatus(OrderItemStatus.CONFIRMED));
        } else if (action.equals("hand-over")) {
            if (!orderPaymentQueryService.read(id).paid(order.getTotalAmount()))
                throw new InvalidOrderException("Order is no longer fully paid; cannot hand over equipment");
            for (var i : items) {
                if (i.getItemType() == OrderItemType.SALE) {
                    inventoryService.fulfillSale(order.getBranchId(), i.getProductId(), i.getQuantity());
                    i.setStatus(OrderItemStatus.FULFILLED);
                } else {
                    if (i.getRentalStartDate().isAfter(LocalDate.now()))
                        throw new InvalidOrderException("Rental has not started yet");
                    inventoryService.handOutRental(order.getBranchId(), i.getProductId(), i.getQuantity());
                    i.setStatus(OrderItemStatus.RENTED);
                }
            }
            order.setStatus(OrderStatus.IN_PROGRESS);
        } else {
            if (order.getCustomerReceivedAt() == null)
                throw new InvalidOrderException("The customer must confirm receipt before return inspection");
            if (items.stream().noneMatch(item -> item.getStatus() == OrderItemStatus.RENTED))
                throw new InvalidOrderException("This order has no equipment awaiting return inspection");
            for (var i : items)
                if (i.getStatus() == OrderItemStatus.RENTED) {
                    inventoryService.receiveRental(order.getBranchId(), i.getProductId(), i.getQuantity());
                    i.setStatus(OrderItemStatus.RETURNED);
                }
            order.setStatus(OrderStatus.COMPLETED);
        }
        return toResponse(order);
    }
}
