package com.coc.sba_treektour.modules.tour.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "event_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    @Setter
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Tạo metadata từ tài sản Cloudinary vừa upload, không cho client tự đặt ID. */
    public EventImage(
            TourEvent event, String imageUrl, String cloudinaryPublicId, String description) {
        this.event = event;
        this.imageUrl = imageUrl;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.description = description;
    }

    /** Gán thời điểm tạo metadata khi ảnh được lưu lần đầu. */
    @PrePersist
    void timestamp() {
        createdAt = OffsetDateTime.now();
    }
}
