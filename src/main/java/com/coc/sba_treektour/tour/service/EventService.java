package com.coc.sba_treektour.tour.service;

import com.coc.sba_treektour.tour.dto.TourDtos.*;
import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.repository.EventRepository;
import com.coc.sba_treektour.tour.repository.ImageRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository events;
    private final CloudinaryImageStorage storage;
    private final ImageRepository images;
    private final ImageValidator validator;

    /** Khởi tạo dịch vụ tour với repository, Cloudinary và bộ kiểm tra ảnh. */
    public EventService(
            EventRepository events,
            ImageRepository images,
            CloudinaryImageStorage storage,
            ImageValidator validator) {
        this.events = events;
        this.images = images;
        this.storage = storage;
        this.validator = validator;
    }

    /** Lọc và phân trang tour; khách chỉ nhận tour đã công bố, quản trị viên có thể lọc mọi trạng thái. */
    public PageResponse<EventSummary> list(
            String keyword,
            EventType type,
            DifficultyLevel difficulty,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            EventStatus status,
            boolean admin,
            int page,
            int size) {
        if ((minPrice != null && minPrice.signum() < 0)
                || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0))
            throw TourException.invalid("Invalid price range");
        String pattern = TourQueries.contains(keyword);
        Specification<TourEvent> spec =
                (root, query, cb) -> {
                    var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    EventStatus filter = admin ? status : EventStatus.PUBLISHED;
                    if (filter != null) predicates.add(cb.equal(root.get("status"), filter));
                    if (type != null) predicates.add(cb.equal(root.get("eventType"), type));
                    if (pattern != null)
                        predicates.add(cb.like(cb.lower(root.get("eventName")), pattern, '!'));
                    if (difficulty != null)
                        predicates.add(
                                cb.equal(root.join("details").get("difficultyLevel"), difficulty));
                    if (minPrice != null) predicates.add(cb.ge(root.get("basePrice"), minPrice));
                    if (maxPrice != null) predicates.add(cb.le(root.get("basePrice"), maxPrice));
                    return cb.and(
                            predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        return PageResponse.of(
                events.findAll(
                                spec,
                                TourQueries.page(
                                        page,
                                        size,
                                        Sort.by(
                                                Sort.Order.desc("createdAt"),
                                                Sort.Order.desc("id"))))
                        .map(this::summary));
    }

    /** Lấy chi tiết tour; trả lỗi 404 nếu khách yêu cầu tour chưa công bố hoặc đã lưu trữ. */
    public EventResponse get(Long id, boolean admin) {
        TourEvent event = find(id);
        if (!admin && event.getStatus() != EventStatus.PUBLISHED) throw TourException.missing();
        return response(event);
    }

    /** Trả ID, trạng thái và giá cơ sở để module lịch sử dụng; không giữ chỗ hay đăng ký. */
    public EventReference reference(Long id) {
        TourEvent event = find(id);
        return new EventReference(event.getId(), event.getStatus(), event.getBasePrice());
    }

    /** Tạo tour ở trạng thái nháp và lưu nội dung, chi tiết trong cùng giao dịch. */
    @Transactional
    public EventResponse create(EventInput input) {
        TourEvent event = new TourEvent();
        apply(event, input);
        return response(events.saveAndFlush(event));
    }

    /** Khóa tour để cập nhật; tour đang công bố phải tiếp tục đáp ứng đủ điều kiện hiển thị. */
    @Transactional
    public EventResponse update(Long id, EventInput input) {
        TourEvent event = editable(id);
        apply(event, input);
        if (event.getStatus() == EventStatus.PUBLISHED) validatePublished(event);
        // ponytail: JPA đã theo dõi entity này; chỉ flush, không merge lại.
        events.flush();
        return response(event);
    }

    /** Đổi trạng thái theo luồng nháp → công bố/lưu trữ; gửi lại trạng thái hiện tại không thay đổi dữ liệu. */
    @Transactional
    public EventResponse status(Long id, EventStatus status) {
        TourEvent event = locked(id);
        if (event.getStatus() == status) return response(event);
        if (event.getStatus() == EventStatus.ARCHIVED || status == EventStatus.DRAFT)
            throw TourException.conflict("Invalid event status transition");
        if (status == EventStatus.PUBLISHED) validatePublished(event);
        event.setStatus(status);
        event.touch();
        events.flush();
        return response(event);
    }

    /** Kiểm tra và tải ảnh lên Cloudinary, lưu metadata; đăng ký dọn ảnh nếu giao dịch DB thất bại. */
    @Transactional
    public ImageResponse upload(Long id, MultipartFile file, String description) {
        TourEvent event = editable(id);
        validateDescription(description);
        var uploaded = storage.upload(validator.validate(file));
        // Dọn ảnh cả khi giao dịch thất bại ở bước commit.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    /** Xóa ảnh vừa tải khi giao dịch không commit; ghi publicId để dọn thủ công nếu Cloudinary lỗi. */
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                storage.delete(uploaded.publicId());
                            } catch (RuntimeException e) {
                                log.error(
                                        "Cloudinary cleanup required for publicId={}",
                                        uploaded.publicId());
                            }
                        }
                    }
                });
        EventImage image = new EventImage();
        image.setEvent(event);
        image.setImageUrl(uploaded.secureUrl());
        image.setCloudinaryPublicId(uploaded.publicId());
        image.setDescription(description);
        event.getImages().add(image);
        event.touch();
        images.saveAndFlush(image);
        return imageResponse(image);
    }

    /** Sửa mô tả của ảnh thuộc đúng tour; không cho phép sửa tour đã lưu trữ. */
    @Transactional
    public ImageResponse describe(Long id, Long imageId, String description) {
        TourEvent event = editable(id);
        validateDescription(description);
        EventImage image = image(event, imageId);
        image.setDescription(description);
        event.touch();
        events.flush();
        return imageResponse(image);
    }

    /** Xóa ảnh trên Cloudinary rồi xóa metadata; giữ ít nhất một ảnh cho tour đã công bố. */
    @Transactional
    public void deleteImage(Long id, Long imageId) {
        TourEvent event = editable(id);
        EventImage image = image(event, imageId);
        if (event.getStatus() == EventStatus.PUBLISHED && event.getImages().size() == 1)
            throw TourException.conflict("Published event requires at least one image");
        storage.delete(image.getCloudinaryPublicId());
        event.getImages().remove(image);
        event.touch();
        events.flush();
    }

    /** Tìm tour theo ID hoặc trả lỗi 404 nếu không tồn tại. */
    private TourEvent find(Long id) {
        return events.findById(id).orElseThrow(TourException::missing);
    }

    /** Lấy tour với khóa ghi để các thao tác đồng thời không làm sai trạng thái hoặc số ảnh. */
    private TourEvent locked(Long id) {
        return events.findForUpdate(id).orElseThrow(TourException::missing);
    }

    /** Khóa tour và từ chối chỉnh sửa khi tour đã lưu trữ. */
    private TourEvent editable(Long id) {
        TourEvent event = locked(id);
        if (event.getStatus() == EventStatus.ARCHIVED)
            throw TourException.conflict("Archived event is read-only");
        return event;
    }

    /** Tìm ảnh trong chính tour đang xử lý, tránh thao tác ảnh của tour khác. */
    private EventImage image(TourEvent event, Long id) {
        return event.getImages().stream()
                .filter(i -> Objects.equals(i.getId(), id))
                .findFirst()
                .orElseThrow(TourException::missing);
    }

    /** Giới hạn mô tả ảnh ở 1.000 ký tự; cho phép không có mô tả. */
    private void validateDescription(String description) {
        if (description != null && description.length() > 1000)
            throw TourException.invalid("Image description exceeds 1000 characters");
    }

    /** Gán nội dung từ request vào tour; xóa chi tiết hiện có nếu request không gửi details. */
    private void apply(TourEvent e, EventInput i) {
        e.setEventName(i.eventName().trim());
        e.setEventType(i.eventType());
        e.setDescription(i.description());
        e.setLocation(i.location());
        e.setBasePrice(i.basePrice());
        e.touch();
        if (i.details() == null) {
            e.attachDetails(null);
            return;
        }
        EventDetail d = e.getDetails();
        if (d == null) {
            d = new EventDetail();
            e.attachDetails(d);
        }
        d.setDurationMinutes(i.details().durationMinutes());
        d.setDifficultyLevel(i.details().difficultyLevel());
        d.setMeetingPoint(i.details().meetingPoint());
        d.setRequirements(i.details().requirements());
    }

    /** Kiểm tra chuỗi null, rỗng hoặc chỉ chứa khoảng trắng. */
    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Yêu cầu đủ nội dung, thời lượng, độ khó, điểm tập trung, yêu cầu và ảnh trước khi công bố. */
    private void validatePublished(TourEvent e) {
        EventDetail d = e.getDetails();
        if (blank(e.getDescription())
                || blank(e.getLocation())
                || d == null
                || d.getDurationMinutes() == null
                || d.getDifficultyLevel() == null
                || blank(d.getMeetingPoint())
                || blank(d.getRequirements())
                || e.getImages().isEmpty())
            throw TourException.conflict(
                    "Publishing requires description, location, duration, difficulty, meeting point, requirements and an image");
    }

    /** Chuyển entity ảnh thành response, không tiết lộ publicId nội bộ của Cloudinary. */
    private ImageResponse imageResponse(EventImage i) {
        return new ImageResponse(i.getId(), i.getImageUrl(), i.getDescription());
    }

    /** Tạo thông tin tóm tắt; dùng ảnh có ID nhỏ nhất làm ảnh đại diện. */
    private EventSummary summary(TourEvent e) {
        return new EventSummary(
                e.getId(),
                e.getEventName(),
                e.getEventType(),
                e.getLocation(),
                e.getBasePrice(),
                e.getStatus(),
                e.getDetails() == null ? null : e.getDetails().getDifficultyLevel(),
                e.getImages().isEmpty() ? null : e.getImages().getFirst().getImageUrl());
    }

    /** Tạo response chi tiết tour, kèm nội dung bổ sung và danh sách ảnh. */
    private EventResponse response(TourEvent e) {
        EventDetail d = e.getDetails();
        return new EventResponse(
                e.getId(),
                e.getEventName(),
                e.getEventType(),
                e.getDescription(),
                e.getLocation(),
                e.getBasePrice(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                d == null
                        ? null
                        : new DetailsInput(
                                d.getDurationMinutes(),
                                d.getDifficultyLevel(),
                                d.getMeetingPoint(),
                                d.getRequirements()),
                e.getImages().stream().map(this::imageResponse).toList());
    }
}
