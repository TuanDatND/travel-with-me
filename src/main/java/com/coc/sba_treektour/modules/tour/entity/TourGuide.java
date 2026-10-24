package com.coc.sba_treektour.modules.tour.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(columnDefinition = "TEXT")
    private String bio;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] languages = new String[0];

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "avatar_public_id", columnDefinition = "TEXT")
    private String avatarPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GuideStatus status = GuideStatus.ACTIVE;
}
