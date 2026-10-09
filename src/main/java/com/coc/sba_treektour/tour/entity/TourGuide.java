package com.coc.sba_treektour.tour.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tour_guides")
@Getter
@Setter
public class TourGuide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_id")
    private Long id;

    // Contract tài khoản chưa READY_TO_MAP: chỉ lưu ID tham chiếu.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String specialization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GuideStatus status = GuideStatus.ACTIVE;
}
