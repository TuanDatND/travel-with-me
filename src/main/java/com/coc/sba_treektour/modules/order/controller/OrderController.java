package com.coc.sba_treektour.modules.order.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.order.dto.request.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;
import com.coc.sba_treektour.modules.order.service.OrderService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận và điều phối các yêu cầu HTTP liên quan đến Đơn hàng (Order).
 * Bao gồm các nghiệp vụ: Đặt hàng, Xem lịch sử đơn, Hủy đơn, Xác nhận nhận hàng,
 * và các bước chuyển trạng thái nghiệp vụ (Duyệt, Điều phối HDV, Bàn giao, Nghiệm thu trả đồ).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    /**
     * API Tạo đơn hàng mới từ giỏ hàng.
     * Method: POST /api/orders
     * Yêu cầu: Đã đăng nhập (khách hàng).
     *
     * @param principal Thông tin tài khoản đăng nhập (lấy email từ JWT).
     * @param request   Dữ liệu tạo đơn (chi nhánh, hình thức giao hàng, danh sách đồ mua/thuê).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(Principal principal, @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Order created successfully", orderService.create(request, principal.getName())));
    }

    /**
     * API Lấy danh sách đơn hàng dành cho Ban quản lý và Nhân viên.
     * Method: GET /api/orders
     * Phân quyền: ADMIN hoặc STAFF.
     *
     * @param userId   (Tùy chọn) Lọc theo ID người đặt hàng.
     * @param branchId (Tùy chọn) Lọc theo ID chi nhánh/trạm (Nhân viên chỉ được xem chi nhánh của mình).
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public ApiResponse<List<OrderResponse>> findAll(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long branchId) {
        return ApiResponse.success(orderService.findAll(userId, branchId));
    }

    /**
     * API Khách hàng lấy danh sách tất cả các đơn hàng của chính mình.
     * Method: GET /api/orders/me
     * Phân quyền: Người dùng đang đăng nhập.
     */
    @GetMapping("/me")
    public ApiResponse<List<OrderResponse>> findMine(Principal principal) {
        return ApiResponse.success(orderService.findMyOrders(principal.getName()));
    }

    /**
     * API Lấy danh sách các đơn đồ dã ngoại đi theo lịch tour cụ thể (hình thức TOUR_DELIVERY).
     * Method: GET /api/orders/schedule/{scheduleId}
     * Phân quyền: ADMIN, STAFF hoặc GUIDE (Hướng dẫn viên phụ trách tour đó).
     *
     * @param scheduleId ID của lịch trình tour.
     */
    @GetMapping("/schedule/{scheduleId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF', 'GUIDE')")
    public ApiResponse<List<OrderResponse>> findBySchedule(@PathVariable Long scheduleId) {
        return ApiResponse.success(orderService.findBySchedule(scheduleId));
    }

    /**
     * API Khách hàng xem thông tin chi tiết một đơn hàng cụ thể.
     * Method: GET /api/orders/{id}
     * Kiểm tra quyền: Chỉ chủ sở hữu đơn hàng mới có quyền xem.
     *
     * @param id ID của đơn hàng.
     */
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> findById(@PathVariable Long id, Principal principal) {
        return ApiResponse.success(orderService.findMyOrder(id, principal.getName()));
    }

    /**
     * API Cập nhật đơn hàng (Hiện tại hệ thống yêu cầu hủy đơn cũ đang PENDING và đặt đơn mới).
     * Method: PUT /api/orders/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<OrderResponse> update(@PathVariable Long id, Principal principal, @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success("Order updated successfully", orderService.update(id, request, principal.getName()));
    }

    /**
     * API Xóa đơn hàng (Thực tế chuyển sang xử lý hủy đơn cancel).
     * Method: DELETE /api/orders/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        orderService.delete(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * API Khách hàng hủy đơn hàng khi chưa thanh toán.
     * Method: POST /api/orders/{id}/cancel
     * Điều kiện: Đơn phải ở trạng thái PENDING và chưa hoàn tất thanh toán tiền.
     *
     * @param id ID của đơn hàng.
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id, Principal principal) {
        return ApiResponse.success(orderService.cancel(id, principal.getName()));
    }

    /**
     * API Khách hàng bấm xác nhận đã nhận được đồ (Confirm Receipt).
     * Method: POST /api/orders/{id}/confirm-receipt
     * Điều kiện: Đơn phải ở trạng thái IN_PROGRESS.
     * Ý nghĩa: Bước bắt buộc để xác nhận đồ đã đến tay khách trước khi cho phép nhân viên nghiệm thu trả đồ (Return).
     *
     * @param id ID của đơn hàng.
     */
    @PostMapping("/{id}/confirm-receipt")
    public ApiResponse<OrderResponse> confirmReceipt(@PathVariable Long id, Principal principal) {
        return ApiResponse.success("Receipt confirmed", orderService.confirmReceipt(id, principal.getName()));
    }

    /**
     * API Thực hiện các hành động chuyển trạng thái nghiệp vụ đơn hàng (Order Operation State Machine).
     * Method: POST /api/orders/{id}/{action}
     * Phân quyền: ADMIN, STAFF hoặc GUIDE (có kiểm tra chi tiết theo vai trò trong Service).
     *
     * Các action hợp lệ:
     * 1. "confirm": Duyệt đơn sau khi khách đã thanh toán đủ 100% -> Chuyển sang CONFIRMED.
     * 2. "guide-pickup": Trạm xuất kho bàn giao đồ cho Tour Guide mang đi tour -> Chuyển sang WITH_GUIDE.
     * 3. "hand-over": Bàn giao đồ trực tiếp cho khách hàng -> Chuyển sang IN_PROGRESS.
     * 4. "return": Nghiệm thu kiểm định và nhận lại đồ thuê từ khách -> Chuyển sang COMPLETED.
     *
     * @param id     ID của đơn hàng.
     * @param action Hành động ("confirm" | "guide-pickup" | "hand-over" | "return").
     */
    @PostMapping("/{id}/{action:confirm|guide-pickup|hand-over|return}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF', 'GUIDE')")
    public ApiResponse<OrderResponse> operate(@PathVariable Long id, @PathVariable String action) {
        return ApiResponse.success(orderService.operateAsActor(id, action));
    }
}
