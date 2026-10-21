package com.coc.sba_treektour.order.service;

import com.coc.sba_treektour.order.dto.*;
import com.coc.sba_treektour.order.entity.*;
import com.coc.sba_treektour.order.repository.OrderRepository;
import com.coc.sba_treektour.account.entity.User;
import com.coc.sba_treektour.account.repository.UserRepository;
import com.coc.sba_treektour.branch.entity.Branch;
import com.coc.sba_treektour.branch.repository.BranchRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, BranchRepository branchRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String email) {
        User user = activeUser(email);
        Branch branch = activeBranch(request.branchId());
        Order order = new Order(user, branch,
                request.orderDate() == null ? OffsetDateTime.now() : request.orderDate(),
                request.status() == null ? OrderStatus.PENDING : request.status(), request.totalAmount());
        order.replaceItems(request.items().stream().map(this::toEntity).toList());
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
        Order order = findEntity(id);
        requireOwner(order, email);
        Branch branch = activeBranch(request.branchId());
        order.update(branch,
                request.orderDate() == null ? order.getOrderDate() : request.orderDate(),
                request.status() == null ? order.getStatus() : request.status(), request.totalAmount());
        order.replaceItems(request.items().stream().map(this::toEntity).toList());
        return toResponse(order);
    }

    @Transactional
    public void delete(Long id, String email) {
        Order order = findEntity(id);
        requireOwner(order, email);
        orderRepository.delete(order);
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
        if (!order.getUser().getEmail().equals(email)) {
            throw new InvalidOrderException("You do not own this order");
        }
    }

    private OrderItem toEntity(CreateOrderItemRequest request) {
        validateRentalDates(request);
        return new OrderItem(request.productId(), request.itemType(), request.quantity(), request.unitPrice(),
                request.rentalStartDate(), request.rentalEndDate(),
                request.status() == null ? OrderItemStatus.PENDING : request.status());
    }

    private void validateRentalDates(CreateOrderItemRequest request) {
        boolean hasDates = request.rentalStartDate() != null || request.rentalEndDate() != null;
        if (request.itemType() == OrderItemType.RENTAL && (!hasDates || request.rentalEndDate().isBefore(request.rentalStartDate()))) {
            throw new InvalidOrderException("Rental items require valid start and end dates");
        }
        if (request.itemType() == OrderItemType.SALE && hasDates) {
            throw new InvalidOrderException("Sale items must not include rental dates");
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getBranchId(), order.getStatus(),
                order.getTotalAmount(), order.getOrderDate(), order.getItems().stream().map(item -> new OrderItemResponse(
                item.getId(), item.getProductId(), item.getItemType(), item.getQuantity(), item.getUnitPrice(),
                item.getRentalStartDate(), item.getRentalEndDate(), item.getStatus())).toList());
    }
}
