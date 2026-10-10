package com.coc.sba_treektour.event.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_images")
@Getter
@Setter
public class EventImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private TourEvent event;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "cloudinary_public_id", columnDefinition = "TEXT")
    private String cloudinaryPublicId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Gán thời điểm tạo metadata khi ảnh được lưu lần đầu. */
    @PrePersist
    void timestamp() {
        createdAt = OffsetDateTime.now();
    }
}
