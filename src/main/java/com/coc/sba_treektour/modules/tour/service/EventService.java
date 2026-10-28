package com.coc.sba_treektour.modules.tour.service;

import com.coc.sba_treektour.modules.tour.dto.request.EventInput;
import com.coc.sba_treektour.modules.tour.dto.response.EventReference;
import com.coc.sba_treektour.modules.tour.dto.response.EventResponse;
import com.coc.sba_treektour.modules.tour.dto.response.EventSummary;
import com.coc.sba_treektour.modules.tour.dto.response.ImageResponse;
import com.coc.sba_treektour.modules.tour.enums.DifficultyLevel;
import com.coc.sba_treektour.modules.tour.enums.EventStatus;
import com.coc.sba_treektour.modules.tour.enums.EventType;
import com.coc.sba_treektour.modules.tour.dto.response.PageResponse;

import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public interface EventService {
    /**
     * Lọc và phân trang tour; khách chỉ nhận tour đã công bố, quản trị viên có thể lọc mọi trạng
     * thái.
     */
    PageResponse<EventSummary> list(
            String keyword,
            EventType type,
            DifficultyLevel difficulty,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            EventStatus status,
            boolean admin,
            int page,
            int size);

    /** Lấy chi tiết tour; trả lỗi 404 nếu khách yêu cầu tour chưa công bố hoặc đã lưu trữ. */
    EventResponse get(Long id, boolean admin);

    /** Trả ID, trạng thái và giá cơ sở để module lịch sử dụng; không giữ chỗ hay đăng ký. */
    EventReference reference(Long id);

    /** Tạo tour ở trạng thái nháp và lưu nội dung, chi tiết trong cùng giao dịch. */
    EventResponse create(EventInput input);

    /** Khóa tour để cập nhật; tour đang công bố phải tiếp tục đáp ứng đủ điều kiện hiển thị. */
    EventResponse update(Long id, EventInput input);

    /**
     * Đổi trạng thái theo luồng nháp → công bố/lưu trữ; gửi lại trạng thái hiện tại không thay đổi
     * dữ liệu.
     */
    EventResponse status(Long id, EventStatus status);

    /**
     * Kiểm tra và tải ảnh lên Cloudinary, lưu metadata; đăng ký dọn ảnh nếu giao dịch DB thất bại.
     */
    ImageResponse upload(Long id, MultipartFile file, String description);

    /** Sửa mô tả của ảnh thuộc đúng tour; không cho phép sửa tour đã lưu trữ. */
    ImageResponse describe(Long id, Long imageId, String description);

    /** Xóa ảnh trên Cloudinary rồi xóa metadata; giữ ít nhất một ảnh cho tour đã công bố. */
    void deleteImage(Long id, Long imageId);
}
