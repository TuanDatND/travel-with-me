package com.coc.sba_treektour.modules.tour.entity;

import com.coc.sba_treektour.modules.tour.enums.GuideStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tour_guides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourGuide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_id")
    private Long id;

    // Contract tài khoản chưa READY_TO_MAP: chỉ lưu ID tham chiếu.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "experience_years")
    @Setter
    private Integer experienceYears;

    @Column(columnDefinition = "TEXT", nullable = false)
    @Setter
    private String specialization;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String bio;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    @Setter
    private String[] languages = new String[0];

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    @Setter
    private String avatarUrl;

    @Column(name = "avatar_public_id", columnDefinition = "TEXT")
    @Setter
    private String avatarPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter
    private GuideStatus status = GuideStatus.ACTIVE;

    /** Liên kết hồ sơ với tài khoản có sẵn; trạng thái mặc định ACTIVE và ID do DB sinh. */
    public TourGuide(Long userId, Integer experienceYears, String specialization) {
        this.userId = userId;
        this.experienceYears = experienceYears;
        this.specialization = specialization;
    }
}
