package com.coc.sba_treektour.event.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tour_events")
@Getter
@Setter
public class TourEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(name = "base_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToOne(
            mappedBy = "event",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private EventDetail details;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<EventImage> images = new ArrayList<>();

    /** Gán thời điểm tạo và cập nhật khi tour được lưu lần đầu. */
    @PrePersist
    void createTimestamps() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    /** Làm mới thời điểm cập nhật trước khi JPA ghi thay đổi tour. */
    @PreUpdate
    void updateTimestamp() {
        updatedAt = OffsetDateTime.now();
    }

    /** Đánh dấu tour thay đổi kể cả khi chỉ sửa chi tiết hoặc ảnh để cập nhật timestamp. */
    public void touch() {
        updatedAt = OffsetDateTime.now();
    }

    /** Gán hoặc xóa chi tiết tour; đồng thời thiết lập liên kết từ chi tiết về tour. */
    public void attachDetails(EventDetail value) {
        details = value;
        if (value != null) value.setEvent(this);
    }
}
