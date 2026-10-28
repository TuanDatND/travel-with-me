package com.coc.sba_treektour.modules.tour.entity;

import com.coc.sba_treektour.modules.tour.enums.DifficultyLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "event_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_detail_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    @Setter
    private TourEvent event;

    @Column(name = "duration")
    @Setter
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", length = 50)
    @Setter
    private DifficultyLevel difficultyLevel;

    @Column(name = "meeting_point", columnDefinition = "TEXT")
    @Setter
    private String meetingPoint;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String requirements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Setter
    private List<ItineraryDay> itinerary = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "packing_items", nullable = false, columnDefinition = "jsonb")
    @Setter
    private List<PackingItem> packingItems = List.of();

    /** Tạo chi tiết gắn với nội dung tour; attachDetails giữ liên kết hai chiều. */
    public EventDetail(TourEvent event) {
        this.event = event;
    }
}
