package com.coc.sba_treektour.tour.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "event_details")
@Getter
@Setter
public class EventDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_detail_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private TourEvent event;

    @Column(name = "duration")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", length = 50)
    private DifficultyLevel difficultyLevel;

    @Column(name = "meeting_point", columnDefinition = "TEXT")
    private String meetingPoint;

    @Column(columnDefinition = "TEXT")
    private String requirements;
}
