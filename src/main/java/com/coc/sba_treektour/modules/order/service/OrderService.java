package com.coc.sba_treektour.modules.order.service;

import com.coc.sba_treektour.modules.order.dto.*;
import com.coc.sba_treektour.modules.order.entity.*;
import com.coc.sba_treektour.modules.order.repository.OrderRepository;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final OrderPaymentReader paymentReader;
    private final com.coc.sba_treektour.modules.product.repository.ProductRepository products;
    private final com.coc.sba_treektour.modules.inventory.service.InventoryService inventory;
    private final com.coc.sba_treektour.modules.inventory.repository.InventoryRepository stocks;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, BranchRepository branchRepository,
            com.coc.sba_treektour.modules.product.repository.ProductRepository products,
            com.coc.sba_treektour.modules.inventory.service.InventoryService inventory,
            com.coc.sba_treektour.modules.inventory.repository.InventoryRepository stocks, OrderPaymentReader paymentReader) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.products = products;
        this.inventory = inventory;
        this.stocks = stocks;
        this.paymentReader = paymentReader;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String email) {
        User user = activeUser(email);
        Branch branch = activeBranch(request.branchId());
        Order order = new Order(user, branch,
                OffsetDateTime.now(), OrderStatus.PENDING, java.math.BigDecimal.ZERO);
        // Lock products in ID order to serialize reservations and stock changes.
        request.items().stream().map(CreateOrderItemRequest::productId).distinct().sorted()
                .forEach(id -> products.lockById(id).orElseThrow(() -> new InvalidOrderException("Product not found: " + id)));
        if (request.items().stream().map(CreateOrderItemRequest::productId).distinct().count() != request.items().size())
            throw new InvalidOrderException("Use one line per product in each order");
        for (var line : request.items()) {
            OrderItem item = toEntity(line);
            if (item.getItemType() == OrderItemType.RENTAL) {
                if (inventory.availability(branch.getId(), item.getProductId(), item.getRentalStartDate(), item.getRentalEndDate()).quantityRentable() < item.getQuantity())
                    throw new InvalidOrderException("Not enough rental capacity");
            } else {
                var stock = stocks.lock(branch.getId(), item.getProductId())
                        .orElseThrow(() -> new InvalidOrderException("No stock at selected branch"));
                if (!"ACTIVE".equals(stock.getStatus()) || stock.getQuantityAvailable() < item.getQuantity())
                    throw new InvalidOrderException("Not enough stock");
            }
            order.addItem(item);
        }
        var total = order.getItems().stream().map(i -> i.getUnitPrice().multiply(java.math.BigDecimal.valueOf(i.getQuantity()))
                .multiply(java.math.BigDecimal.valueOf(i.getItemType() == OrderItemType.RENTAL
                    ? java.time.temporal.ChronoUnit.DAYS.between(i.getRentalStartDate(), i.getRentalEndDate()) + 1 : 1)))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        order.update(branch, order.getOrderDate(), OrderStatus.PENDING, total);
        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) { return toResponse(findEntity(id)); }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll(Long userId) {
        List<Order> orders = userId == null ? orderRepository.findAll() : orderRepository.findByUser_IdOrderByOrderDateDesc(userId);
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse update(Long id, CreateOrderRequest request, String email) {
        throw new InvalidOrderException("Cancel the pending order and create a new order to change its items");
    }

    @Transactional
    public void delete(Long id, String email) {
        cancel(id, email);
    }

    @Transactional(readOnly = true)
    public OrderResponse findMyOrder(Long id, String email) {
        Order order = findEntity(id);
        requireOwner(order, email);
        return toResponse(order);
    }

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
            throw new org.springframework.security.access.AccessDeniedException("You do not own this order");
        }
    }

    private OrderItem toEntity(CreateOrderItemRequest request) {
        validateRentalDates(request);
        var product = products.findById(request.productId()).filter(p -> "ACTIVE".equals(p.getStatus()))
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
        var balance = paymentReader.read(order.getId());
        boolean pending = order.getStatus() == OrderStatus.PENDING;
        return new OrderResponse(order.getId(), order.getUserId(), order.getBranchId(), order.getStatus(),
                order.getTotalAmount(), order.getOrderDate(), order.getItems().stream().map(item -> new OrderItemResponse(
                item.getId(), item.getProductId(), item.getItemType(), item.getQuantity(), item.getUnitPrice(),
                item.getRentalStartDate(), item.getRentalEndDate(), item.getStatus())).toList(),
                balance.status(order.getTotalAmount()), balance.net(),
                order.getTotalAmount().subtract(balance.net()).max(java.math.BigDecimal.ZERO),
                pending && balance.paid(order.getTotalAmount()), pending && balance.cancellable());
    }

    @Transactional
    public OrderResponse cancel(Long id, String email) {
        Order order = orderRepository.lockById(id).orElseThrow(() -> new OrderNotFoundException(id));
        requireOwner(order, email);
        if (order.getStatus() != OrderStatus.PENDING) throw new InvalidOrderException("Only pending orders can be cancelled");
        if (!paymentReader.read(id).cancellable())
            throw new InvalidOrderException("Payment received or processing; cancellation requires the refund workflow");
        order.setStatus(OrderStatus.CANCELLED);
        order.getItems().forEach(i -> i.setStatus(OrderItemStatus.CANCELLED));
        return toResponse(order);
    }

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
        var items = order.getItems().stream().sorted(java.util.Comparator.comparing(OrderItem::getProductId)).toList();
        if (action.equals("confirm")) {
            if (!paymentReader.read(id).paid(order.getTotalAmount()))
                throw new InvalidOrderException("Order must be fully paid before admin approval");
            order.setStatus(OrderStatus.CONFIRMED);
            items.forEach(i -> i.setStatus(OrderItemStatus.CONFIRMED));
        } else if (action.equals("hand-over")) {
            if (!paymentReader.read(id).paid(order.getTotalAmount()))
                throw new InvalidOrderException("Order is no longer fully paid; cannot hand over equipment");
            for (var i : items) {
                if (i.getItemType() == OrderItemType.SALE) {
                    inventory.fulfillSale(order.getBranchId(), i.getProductId(), i.getQuantity());
                    i.setStatus(OrderItemStatus.FULFILLED);
                } else {
                    if (i.getRentalStartDate().isAfter(java.time.LocalDate.now())) throw new InvalidOrderException("Rental has not started yet");
                    inventory.handOutRental(order.getBranchId(), i.getProductId(), i.getQuantity());
                    i.setStatus(OrderItemStatus.RENTED);
                }
            }
            order.setStatus(items.stream().anyMatch(i -> i.getItemType() == OrderItemType.RENTAL) ? OrderStatus.IN_PROGRESS : OrderStatus.COMPLETED);
        } else {
            for (var i : items) if (i.getStatus() == OrderItemStatus.RENTED) {
                inventory.receiveRental(order.getBranchId(), i.getProductId(), i.getQuantity());
                i.setStatus(OrderItemStatus.RETURNED);
            }
            order.setStatus(OrderStatus.COMPLETED);
        }
        return toResponse(order);
    }
}
