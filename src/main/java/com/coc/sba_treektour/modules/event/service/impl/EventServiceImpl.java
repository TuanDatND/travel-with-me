package com.coc.sba_treektour.modules.event.service.impl;

import com.coc.sba_treektour.modules.event.dto.request.EventInput;
import com.coc.sba_treektour.modules.event.dto.request.ItineraryDayInput;
import com.coc.sba_treektour.modules.event.dto.response.ActivityResponse;
import com.coc.sba_treektour.modules.event.dto.response.DetailsResponse;
import com.coc.sba_treektour.modules.event.dto.response.EventReference;
import com.coc.sba_treektour.modules.event.dto.response.EventResponse;
import com.coc.sba_treektour.modules.event.dto.response.EventSummary;
import com.coc.sba_treektour.modules.event.dto.response.ImageResponse;
import com.coc.sba_treektour.modules.event.dto.response.ItineraryDayResponse;
import com.coc.sba_treektour.modules.event.dto.response.PackingItemResponse;
import com.coc.sba_treektour.modules.event.entity.EventDetail;
import com.coc.sba_treektour.modules.event.entity.EventImage;
import com.coc.sba_treektour.modules.event.entity.ItineraryDay;
import com.coc.sba_treektour.modules.event.entity.PackingItem;
import com.coc.sba_treektour.modules.event.entity.TourEvent;
import com.coc.sba_treektour.modules.event.enums.DifficultyLevel;
import com.coc.sba_treektour.modules.event.enums.EventStatus;
import com.coc.sba_treektour.modules.event.enums.EventType;
import com.coc.sba_treektour.modules.event.repository.EventRepository;
import com.coc.sba_treektour.modules.event.repository.ImageRepository;
import com.coc.sba_treektour.modules.event.service.EventService;
import com.coc.sba_treektour.modules.event.storage.CloudinaryImageStorage;
import com.coc.sba_treektour.modules.event.validator.ImageValidator;
import com.coc.sba_treektour.modules.tour.dto.response.PageResponse;
import com.coc.sba_treektour.modules.tour.exception.TourException;
import com.coc.sba_treektour.modules.tour.util.TourQueries;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private static final Logger log = LoggerFactory.getLogger(EventServiceImpl.class);
    private final EventRepository events;
    private final ImageRepository images;
    private final CloudinaryImageStorage storage;
    private final ImageValidator validator;

    /**
     * Lọc và phân trang tour; khách chỉ nhận tour đã công bố, quản trị viên có thể lọc mọi trạng
     * thái.
     */
    @Override
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
        return PageResponse.of(
                events.findFiltered(
                                pattern,
                                type,
                                difficulty,
                                minPrice,
                                maxPrice,
                                admin ? status : EventStatus.PUBLISHED,
                                TourQueries.page(
                                        page,
                                        size,
                                        Sort.by(
                                                Sort.Order.desc("createdAt"),
                                                Sort.Order.desc("id"))))
                        .map(this::summary));
    }

    /** Lấy chi tiết tour; trả lỗi 404 nếu khách yêu cầu tour chưa công bố hoặc đã lưu trữ. */
    @Override
    public EventResponse get(Long id, boolean admin) {
        TourEvent event = find(id);
        if (!admin && event.getStatus() != EventStatus.PUBLISHED) throw TourException.missing();
        return response(event);
    }

    /** Trả ID, trạng thái và giá cơ sở để module lịch sử dụng; không giữ chỗ hay đăng ký. */
    @Override
    public EventReference reference(Long id) {
        TourEvent event = find(id);
        return new EventReference(event.getId(), event.getStatus(), event.getBasePrice());
    }

    /** Tạo tour ở trạng thái nháp và lưu nội dung, chi tiết trong cùng giao dịch. */
    @Transactional
    @Override
    public EventResponse create(EventInput input) {
        TourEvent event = new TourEvent(input.eventName(), input.eventType(), input.basePrice());
        apply(event, input);
        return response(events.saveAndFlush(event));
    }

    /** Khóa tour để cập nhật; tour đang công bố phải tiếp tục đáp ứng đủ điều kiện hiển thị. */
    @Transactional
    @Override
    public EventResponse update(Long id, EventInput input) {
        TourEvent event = editable(id);
        apply(event, input);
        if (event.getStatus() == EventStatus.PUBLISHED) validatePublished(event);
        // ponytail: JPA đã theo dõi entity này; chỉ flush, không merge lại.
        events.flush();
        return response(event);
    }

    /**
     * Đổi trạng thái theo luồng nháp → công bố/lưu trữ; gửi lại trạng thái hiện tại không thay đổi
     * dữ liệu.
     */
    @Transactional
    @Override
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

    /**
     * Kiểm tra và tải ảnh lên Cloudinary, lưu metadata; đăng ký dọn ảnh nếu giao dịch DB thất bại.
     */
    @Transactional
    @Override
    public ImageResponse upload(Long id, MultipartFile file, String description) {
        TourEvent event = editable(id);
        validateDescription(description);
        var uploaded = storage.upload(validator.validate(file));
        // Dọn ảnh cả khi giao dịch thất bại ở bước commit.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    /**
                     * Xóa ảnh vừa tải khi giao dịch không commit; ghi publicId để dọn thủ công nếu
                     * Cloudinary lỗi.
                     */
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
        EventImage image =
                new EventImage(event, uploaded.secureUrl(), uploaded.publicId(), description);
        event.getImages().add(image);
        event.touch();
        images.saveAndFlush(image);
        return imageResponse(image);
    }

    /** Sửa mô tả của ảnh thuộc đúng tour; không cho phép sửa tour đã lưu trữ. */
    @Transactional
    @Override
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
    @Override
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
        var days = i.details().itinerary();
        for (int index = 0; index < days.size(); index++) {
            if (days.get(index).day() != index + 1)
                throw TourException.invalid("Itinerary days must be ordered consecutively from 1");
        }
        EventDetail d = e.getDetails();
        if (d == null) {
            d = new EventDetail(e);
            e.attachDetails(d);
        }
        d.setDurationMinutes(i.details().durationMinutes());
        d.setDifficultyLevel(i.details().difficultyLevel());
        d.setMeetingPoint(i.details().meetingPoint());
        d.setRequirements(i.details().requirements());
        d.setItinerary(days.stream().map(this::dayEntity).toList());
        d.setPackingItems(
                i.details().packingItems().stream()
                        .map(item -> new PackingItem(item.text().trim(), item.required()))
                        .toList());
    }

    /** Chuyển một ngày và các hoạt động từ request thành giá trị lưu JSONB. */
    private ItineraryDay dayEntity(ItineraryDayInput day) {
        return new ItineraryDay(
                day.day(),
                day.title().trim(),
                day.summary(),
                day.distanceKm(),
                day.elevation(),
                day.highlights(),
                day.activities().stream()
                        .map(
                                a ->
                                        new ItineraryDay.Activity(
                                                a.time().trim(),
                                                a.title().trim(),
                                                a.location(),
                                                a.description(),
                                                a.icon() == null ? "hike" : a.icon()))
                        .toList());
    }

    /** Chuyển nội dung đã lưu thành response, giữ nguyên thứ tự các hoạt động. */
    private ItineraryDayResponse dayResponse(ItineraryDay day) {
        return new ItineraryDayResponse(
                day.day(),
                day.title(),
                day.summary(),
                day.distanceKm(),
                day.elevation(),
                day.highlights(),
                day.activities().stream()
                        .map(
                                a ->
                                        new ActivityResponse(
                                                a.time(),
                                                a.title(),
                                                a.location(),
                                                a.description(),
                                                a.icon()))
                        .toList());
    }

    /** Kiểm tra chuỗi null, rỗng hoặc chỉ chứa khoảng trắng. */
    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Yêu cầu đủ nội dung, thời lượng, độ khó, điểm tập trung, yêu cầu và ảnh trước khi công bố.
     */
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
                    "Publishing requires description, location, duration, difficulty, meeting"
                            + " point, requirements and an image");
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
                        : new DetailsResponse(
                                d.getDurationMinutes(),
                                d.getDifficultyLevel(),
                                d.getMeetingPoint(),
                                d.getRequirements(),
                                d.getItinerary().stream().map(this::dayResponse).toList(),
                                d.getPackingItems().stream()
                                        .map(
                                                item ->
                                                        new PackingItemResponse(
                                                                item.text(), item.required()))
                                        .toList()),
                e.getImages().stream().map(this::imageResponse).toList());
    }
}
