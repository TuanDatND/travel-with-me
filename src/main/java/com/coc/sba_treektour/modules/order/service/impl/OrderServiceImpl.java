package com.coc.sba_treektour.modules.order.service.impl;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.inventory.repository.InventoryRepository;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderItemRequest;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderRequest;
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
import com.coc.sba_treektour.modules.payment.dto.response.OrderPaymentBalance;
import com.coc.sba_treektour.modules.payment.service.OrderPaymentQueryService;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.security.ScheduleAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final OrderPaymentQueryService orderPaymentQueryService;
    private final EventScheduleRepository eventScheduleRepository;
    private final ScheduleAccessPolicy scheduleAccessPolicy;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            UserRepository userRepository,
            BranchRepository branchRepository,
            ProductRepository productRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            OrderPaymentQueryService orderPaymentQueryService) {
        this(orderRepository, userRepository, branchRepository, productRepository, inventoryService, inventoryRepository, orderPaymentQueryService, null, null);
    }

    @Autowired
    public OrderServiceImpl(
            OrderRepository orderRepository,
            UserRepository userRepository,
            BranchRepository branchRepository,
            ProductRepository productRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            OrderPaymentQueryService orderPaymentQueryService,
            EventScheduleRepository eventScheduleRepository,
            ScheduleAccessPolicy scheduleAccessPolicy) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.orderPaymentQueryService = orderPaymentQueryService;
        this.eventScheduleRepository = eventScheduleRepository;
        this.scheduleAccessPolicy = scheduleAccessPolicy;
    }

    /**
     * Tạo đơn hàng mới từ giỏ hàng.
     * 1. Kiểm tra tài khoản (phải ACTIVE và đã cập nhật họ tên, SĐT, địa chỉ giao nhận).
     * 2. Khóa bi quan (pessimistic lock) các sản phẩm theo thứ tự ID tăng dần để chống deadlock và tranh chấp tồn kho.
     * 3. Tính tổng tiền: Đồ bán = Giá * SL; Đồ thuê = Giá thuê/ngày * SL * (Số ngày thuê).
     * 4. Lưu đơn ở trạng thái PENDING cùng thông tin giao hàng (Tại trạm hoặc Giao theo tour).
     */
    @Override
    @Transactional
    public OrderResponse create(CreateOrderRequest request, String email) {
        User user = activeUser(email);
        Branch branch = activeBranch(request.branchId());
        if (user.getFullName() == null || user.getFullName().isBlank()
                || user.getPhone() == null || !user.getPhone().trim().matches("[+0-9\\s().-]{7,30}")
                || user.getAddress() == null || user.getAddress().isBlank()) {
            throw new InvalidOrderException("Please update your profile with your full name, phone number and address before placing an order");
        }
        Order order = request.toEntity(user, branch);
        order.setCustomerContact(user.getFullName(), user.getPhone(), user.getAddress());
        // Lock products in ID order to serialize reservations and stock changes.
        request.items().stream().map(CreateOrderItemRequest::productId).distinct().sorted()
                .forEach(id -> productRepository.lockById(id).orElseThrow(() -> new InvalidOrderException("Product not found: " + id)));
        if (request.items().stream().map(CreateOrderItemRequest::productId).distinct().count() != request.items().size())
            throw new InvalidOrderException("Use one line per product in each order");
        for (var line : request.items()) {
            var product = productRepository.findById(line.productId()).filter(p -> "ACTIVE".equals(p.getStatus()))
                    .orElseThrow(() -> new InvalidOrderException("Active product not found"));
            OrderItem item = line.toEntity(product);
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
        return findAll(userId, null);
    }

    /**
     * Lấy danh sách đơn hàng cho Admin/Staff:
     * - Nếu là Staff: kiểm tra quyền quản lý chi nhánh (`manages(branchId)`).
     * - Nếu là Admin: có thể lọc theo chi nhánh, khách hàng hoặc xem toàn bộ.
     * - Tự động truy vấn số dư thanh toán (`OrderPaymentBalance`) từ module Payment gắn vào response.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findAll(Long userId, Long branchId) {
        List<Order> orders;
        if (scheduleAccessPolicy != null && !scheduleAccessPolicy.hasRole("ADMIN")) {
            if (branchId != null) {
                scheduleAccessPolicy.requireManager(branchId);
                orders = orderRepository.findByBranch_IdOrderByOrderDateDesc(branchId);
            } else {
                orders = orderRepository.findAll().stream()
                        .filter(o -> scheduleAccessPolicy.manages(o.getBranchId()))
                        .toList();
            }
        } else {
            if (branchId != null) {
                orders = orderRepository.findByBranch_IdOrderByOrderDateDesc(branchId);
            } else if (userId != null) {
                orders = orderRepository.findByUser_IdOrderByOrderDateDesc(userId);
            } else {
                orders = orderRepository.findAll();
            }
        }
        if (userId != null && branchId != null) {
            orders = orders.stream().filter(o -> o.getUserId().equals(userId)).toList();
        }
        var balances = orderPaymentQueryService.readMany(orders.stream().map(Order::getId).toList());
        var unpaid = OrderPaymentBalance.EMPTY;
        return orders.stream().map(order -> OrderResponse.from(order, balances.getOrDefault(order.getId(), unpaid))).toList();
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

    /**
     * Khách hàng lấy danh sách tất cả các đơn hàng do chính mình đặt.
     * Sắp xếp theo ngày đặt giảm dần (mới nhất lên đầu) kèm thông tin thanh toán.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findMyOrders(String email) {
        Long userId = activeUser(email).getId();
        // Customer ownership is independent of administrative branch-management access.
        List<Order> orders = orderRepository.findByUser_IdOrderByOrderDateDesc(userId);
        var balances = orderPaymentQueryService.readMany(orders.stream().map(Order::getId).toList());
        return orders.stream().map(order -> OrderResponse.from(order,
                balances.getOrDefault(order.getId(), OrderPaymentBalance.EMPTY))).toList();
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

    private OrderResponse toResponse(Order order) {
        return OrderResponse.from(order, orderPaymentQueryService.read(order.getId()));
    }

    /**
     * Khách hàng hủy đơn hàng (Cancel Order):
     * 1. Xác thực người gọi là chủ sở hữu của đơn hàng (`requireOwner`).
     * 2. Chỉ cho phép hủy khi đơn ở trạng thái PENDING.
     * 3. Kiểm tra phía thanh toán: nếu đã thanh toán hoặc đang xử lý giao dịch thì không được hủy trực tiếp (phải qua luồng Refund).
     * 4. Đổi trạng thái Order và tất cả OrderItem sang CANCELLED.
     */
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

    /**
     * Khách hàng xác nhận đã nhận hàng (Confirm Customer Receipt):
     * 1. Kiểm tra đơn phải đang ở trạng thái IN_PROGRESS (đã được nhân viên bàn giao tại quầy hoặc HDV giao tại tour).
     * 2. Ghi nhận thời điểm nhận hàng `customerReceivedAt = now()`.
     * 3. Nếu đơn hàng chỉ bao gồm đồ mua (SALE, không có đồ thuê RENTAL), đơn được hoàn tất (COMPLETED) ngay lập tức.
     */
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

    /**
     * Máy trạng thái đơn hàng (Order State Machine transitions):
     *
     * 1. action = "confirm":
     *    - Đơn phải đang PENDING, đã thanh toán đủ 100%, không bị treo yêu cầu hoàn tiền (refund pending).
     *    - Chuyển trạng thái Order và các Items sang CONFIRMED.
     *
     * 2. action = "guide-pickup":
     *    - Áp dụng riêng cho đơn giao theo tour (TOUR_DELIVERY).
     *    - Đơn phải đang CONFIRMED. Xuất kho trạm bàn giao đồ cho Tour Guide mang đi.
     *    - Chuyển trạng thái Order sang WITH_GUIDE.
     *
     * 3. action = "hand-over":
     *    - Đơn tại trạm (STORE_PICKUP): nhân viên bàn giao trực tiếp cho khách, trừ tồn kho bán hoặc đánh dấu đồ thuê đang xuất ngoại.
     *    - Đơn theo tour (TOUR_DELIVERY): Tour Guide bàn giao đồ tận tay khách tại điểm tập kết tour.
     *    - Chuyển trạng thái Order sang IN_PROGRESS.
     *
     * 4. action = "return":
     *    - Áp dụng khi khách trả đồ thuê (RENTAL).
     *    - Yêu cầu bắt buộc: Khách phải bấm xác nhận đã nhận đồ trước đó (`customerReceivedAt != null`).
     *    - Nghiệm thu đồ thuê: Kiểm tra đồ trả, hoàn trả số lượng vào kho khả dụng của chi nhánh (`receiveRental`).
     *    - Cập nhật trạng thái từng món đồ sang RETURNED và hoàn tất đơn hàng sang COMPLETED.
     */
    @Override
    @Transactional
    public OrderResponse operate(Long id, String action) {
        Order order = orderRepository.lockById(id).orElseThrow(() -> new OrderNotFoundException(id));
        boolean isTourDelivery = "TOUR_DELIVERY".equals(order.getDeliveryType());
        OrderStatus expected = switch (action) {
            case "confirm" -> OrderStatus.PENDING;
            case "guide-pickup" -> OrderStatus.CONFIRMED;
            case "hand-over" -> isTourDelivery ? OrderStatus.WITH_GUIDE : OrderStatus.CONFIRMED;
            case "return" -> OrderStatus.IN_PROGRESS;
            default -> throw new InvalidOrderException("Invalid action");
        };
        if (order.getStatus() != expected) throw new InvalidOrderException("Order must be " + expected);
        var items = order.getItems().stream().sorted(Comparator.comparing(OrderItem::getProductId)).toList();
        if (orderPaymentQueryService.read(id).refundPending())
            throw new InvalidOrderException("Refund is pending; approval and handover are blocked");
        if (action.equals("confirm")) {
            if (!orderPaymentQueryService.read(id).paid(order.getTotalAmount()))
                throw new InvalidOrderException("Order must be fully paid before admin approval");
            order.setStatus(OrderStatus.CONFIRMED);
            items.forEach(i -> i.setStatus(OrderItemStatus.CONFIRMED));
        } else if (action.equals("guide-pickup")) {
            if (!isTourDelivery)
                throw new InvalidOrderException("Only tour delivery orders can be dispatched to a guide");
            if (!orderPaymentQueryService.read(id).paid(order.getTotalAmount()))
                throw new InvalidOrderException("Order is no longer fully paid; cannot dispatch equipment to guide");
            fulfillItemsOutOfStore(order.getBranchId(), items, false);
            order.setStatus(OrderStatus.WITH_GUIDE);
        } else if (action.equals("hand-over")) {
            if (!orderPaymentQueryService.read(id).paid(order.getTotalAmount()))
                throw new InvalidOrderException("Order is no longer fully paid; cannot hand over equipment");
            if (!isTourDelivery) {
                fulfillItemsOutOfStore(order.getBranchId(), items, true);
            }
            order.setStatus(OrderStatus.IN_PROGRESS);
        } else {
            if (order.getCustomerReceivedAt() == null)
                throw new InvalidOrderException("The customer must confirm receipt before return inspection");
            var returnableRentals = items.stream()
                    .filter(item -> item.getItemType() == OrderItemType.RENTAL && item.getStatus() != OrderItemStatus.RETURNED && item.getStatus() != OrderItemStatus.CANCELLED)
                    .toList();
            if (returnableRentals.isEmpty())
                throw new InvalidOrderException("This order has no equipment awaiting return inspection");
            for (var i : returnableRentals) {
                if (i.getStatus() == OrderItemStatus.RENTED) {
                    inventoryService.receiveRental(order.getBranchId(), i.getProductId(), i.getQuantity());
                }
                i.setStatus(OrderItemStatus.RETURNED);
            }
            order.setStatus(OrderStatus.COMPLETED);
        }
        return toResponse(order);
    }

    /**
     * Xuất kho thực tế khi bàn giao đồ:
     * - Đồ bán (SALE): Trừ vĩnh viễn tồn kho khả dụng (`fulfillSale`).
     * - Đồ thuê (RENTAL): Kiểm tra ngày bắt đầu thuê không được ở tương lai, ghi nhận xuất kho cho thuê (`handOutRental`).
     */
    private void fulfillItemsOutOfStore(Long branchId, List<OrderItem> items, boolean checkRentalStartDate) {
        for (var i : items) {
            if (i.getStatus() == OrderItemStatus.FULFILLED || i.getStatus() == OrderItemStatus.RENTED) {
                continue;
            }
            if (i.getItemType() == OrderItemType.SALE) {
                inventoryService.fulfillSale(branchId, i.getProductId(), i.getQuantity());
                i.setStatus(OrderItemStatus.FULFILLED);
            } else {
                if (checkRentalStartDate && i.getRentalStartDate().isAfter(LocalDate.now()))
                    throw new InvalidOrderException("Rental has not started yet");
                inventoryService.handOutRental(branchId, i.getProductId(), i.getQuantity());
                i.setStatus(OrderItemStatus.RENTED);
            }
        }
    }

    /**
     * Lấy các đơn hàng đồ dã ngoại đi theo một lịch tour cụ thể (TOUR_DELIVERY).
     * Chỉ Tour Guide phụ trách tour đó hoặc Quản lý trạm mới có quyền xem danh sách này.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findBySchedule(Long scheduleId) {
        var schedule = eventScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new InvalidOrderException("Schedule not found: " + scheduleId));
        scheduleAccessPolicy.requireAssignedGuideOrManager(schedule);
        List<Order> orders = orderRepository.findByScheduleIdAndDeliveryTypeOrderByOrderDateDesc(scheduleId, "TOUR_DELIVERY");
        var balances = orderPaymentQueryService.readMany(orders.stream().map(Order::getId).toList());
        var unpaid = OrderPaymentBalance.EMPTY;
        return orders.stream().map(order -> OrderResponse.from(order, balances.getOrDefault(order.getId(), unpaid))).toList();
    }

    /**
     * Kiểm tra phân quyền theo vai trò (Admin, Staff, Guide) trước khi thực hiện thao tác nghiệp vụ:
     * - Admin: Bỏ qua kiểm tra, có thể thao tác mọi đơn trên toàn hệ thống.
     * - Staff: Kiểm tra quyền quản lý chi nhánh (`requireManager(branchId)`).
     * - Guide: Chỉ được thao tác đơn giao theo tour của lịch trình mà hướng dẫn viên đó được phân công.
     *          Guide không được phép duyệt đơn (`confirm`) hoặc tự ý nhận đồ trước khi trạm bàn giao.
     */
    @Override
    @Transactional
    public OrderResponse operateAsActor(Long id, String action) {
        if (scheduleAccessPolicy != null && !scheduleAccessPolicy.hasRole("ADMIN")) {
            Order order = findEntity(id);
            if (scheduleAccessPolicy.hasRole("STAFF")) {
                scheduleAccessPolicy.requireManager(order.getBranchId());
            } else {
                if (!"TOUR_DELIVERY".equals(order.getDeliveryType()) || order.getScheduleId() == null) {
                    throw new AccessDeniedException("Only tour delivery orders for an assigned schedule can be managed here");
                }
                var schedule = eventScheduleRepository.findById(order.getScheduleId())
                        .orElseThrow(() -> new InvalidOrderException("Schedule not found: " + order.getScheduleId()));
                scheduleAccessPolicy.requireAssignedGuideOrManager(schedule);
                if ("confirm".equals(action) || "guide-pickup".equals(action)) {
                    throw new AccessDeniedException("Only store staff or admins can approve orders and dispatch gear to the guide");
                }
            }
        }
        return operate(id, action);
    }
}
