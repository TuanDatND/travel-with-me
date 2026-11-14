package com.coc.sba_treektour.modules.order.service;

import com.coc.sba_treektour.modules.order.dto.request.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;
import java.util.List;

/**
 * Service quản lý vòng đời đơn hàng (Order) bao gồm mua đứt (SALE) và thuê đồ trekking (RENTAL).
 */
public interface OrderService {
    /**
     * Tạo đơn hàng mới từ giỏ hàng.
     * Validate thông tin khách hàng, khóa sản phẩm tránh xung đột tồn kho,
     * tính tổng tiền (áp dụng quy tắc thuê theo ngày), và lưu đơn ở trạng thái PENDING.
     *
     * @param request Dữ liệu đơn hàng gồm branchId, deliveryType, scheduleId, danh sách item.
     * @param email   Email tài khoản đặt hàng (lấy từ JWT token / Principal).
     * @return Thông tin chi tiết đơn hàng vừa tạo.
     */
    OrderResponse create(CreateOrderRequest request, String email);

    /**
     * Tìm chi tiết đơn hàng theo ID.
     *
     * @param id ID của đơn hàng.
     * @return DTO OrderResponse kèm số dư thanh toán.
     */
    OrderResponse findById(Long id);

    /**
     * Lấy danh sách tất cả đơn hàng theo người dùng (dành cho Admin/Staff).
     */
    List<OrderResponse> findAll(Long userId);

    /**
     * Lấy danh sách đơn hàng có phân quyền:
     * - Admin xem được tất cả các chi nhánh.
     * - Staff chỉ xem được các đơn thuộc chi nhánh mình quản lý.
     *
     * @param userId   Lọc theo khách hàng (nếu có).
     * @param branchId Lọc theo chi nhánh (nếu có).
     */
    default List<OrderResponse> findAll(Long userId, Long branchId) {
        return findAll(userId);
    }

    /**
     * Lấy danh sách các đơn đồ dã ngoại giao theo tour (TOUR_DELIVERY) gắn với một lịch tour cụ thể.
     * Chỉ Tour Guide được phân công lịch tour hoặc Quản lý trạm mới có quyền truy cập.
     *
     * @param scheduleId ID của lịch trình tour.
     */
    List<OrderResponse> findBySchedule(Long scheduleId);

    /**
     * Cập nhật đơn hàng (không cho phép sửa trực tiếp, bắt buộc hủy đơn pending và đặt lại).
     */
    OrderResponse update(Long id, CreateOrderRequest request, String email);

    /**
     * Xóa đơn hàng (ủy quyền sang xử lý hủy đơn cancel).
     */
    void delete(Long id, String email);

    /**
     * Khách hàng xem chi tiết 1 đơn hàng của chính mình (kiểm tra quyền sở hữu).
     */
    OrderResponse findMyOrder(Long id, String email);

    /**
     * Khách hàng lấy danh sách tất cả các đơn mua / thuê của chính mình.
     *
     * @param email Email người dùng đăng nhập.
     */
    List<OrderResponse> findMyOrders(String email);

    /**
     * Khách hàng hủy đơn hàng đang ở trạng thái PENDING.
     * Chỉ cho phép hủy khi đơn chưa thanh toán hoặc chưa có giao dịch thanh toán thành công.
     */
    OrderResponse cancel(Long id, String email);

    /**
     * Thực hiện chuyển trạng thái nghiệp vụ đơn hàng (Core State Machine):
     * - confirm: Admin/Staff duyệt đơn sau khi đơn đã được thanh toán 100%.
     * - guide-pickup: Trạm xuất kho bàn giao đồ cho Tour Guide mang đi tour.
     * - hand-over: Trạm xuất kho bàn giao đồ trực tiếp cho khách hàng (chuyển sang IN_PROGRESS).
     * - return: Kiểm định và nhận lại đồ thuê từ khách (hoàn tất sang COMPLETED).
     *
     * @param id     ID đơn hàng.
     * @param action Hành động nghiệp vụ ("confirm" | "guide-pickup" | "hand-over" | "return").
     */
    OrderResponse operate(Long id, String action);

    /**
     * Thực hiện chuyển trạng thái nghiệp vụ kèm kiểm tra phân quyền người thực hiện (Actor-based security):
     * - Admin: Có toàn quyền trên mọi chi nhánh.
     * - Staff: Chỉ được duyệt, xuất kho, nhận trả đồ tại chi nhánh phụ trách.
     * - Guide: Chỉ được xác nhận nhận đồ mang theo tour đối với lịch tour được giao.
     */
    OrderResponse operateAsActor(Long id, String action);

    /**
     * Khách hàng xác nhận đã nhận được đồ (Confirm Receipt).
     * Bắt buộc khách phải xác nhận bước này trước khi nhân viên có thể nghiệm thu trả đồ (Return inspection).
     * Nếu đơn chỉ toàn đồ mua (SALE), đơn tự động chuyển sang COMPLETED.
     */
    OrderResponse confirmReceipt(Long id, String email);
}
