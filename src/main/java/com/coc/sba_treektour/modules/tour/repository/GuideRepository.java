package com.coc.sba_treektour.modules.tour.repository;

import com.coc.sba_treektour.modules.tour.entity.TourGuide;
import com.coc.sba_treektour.modules.tour.enums.GuideStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Optional;

public interface GuideRepository
        extends JpaRepository<TourGuide, Long>, JpaSpecificationExecutor<TourGuide> {
    /** Khóa hồ sơ khi sửa để tránh mất cập nhật hoặc ghi đè ảnh đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from TourGuide g where g.id = :id")
    Optional<TourGuide> findForUpdate(@Param("id") Long id);

    /** Phân trang hồ sơ ACTIVE có tài khoản ACTIVE, kèm bộ lọc chuyên môn. */
    @Query(
            value =
                    """
                    SELECT g.* FROM tour_guides g JOIN users u ON u.user_id = g.user_id
                    WHERE g.status = 'ACTIVE' AND u.status = 'ACTIVE'
                    AND (:specialization IS NULL OR LOWER(g.specialization) LIKE :specialization ESCAPE '!')
                    """,
            countQuery =
                    """
                    SELECT count(*) FROM tour_guides g JOIN users u ON u.user_id = g.user_id
                    WHERE g.status = 'ACTIVE' AND u.status = 'ACTIVE'
                    AND (:specialization IS NULL OR LOWER(g.specialization) LIKE :specialization ESCAPE '!')
                    """,
            nativeQuery = true)
    Page<TourGuide> findPublic(@Param("specialization") String specialization, Pageable pageable);

    /** Gom bộ lọc hồ sơ quản trị tại repository, service chỉ điều phối và kiểm tra nghiệp vụ. */
    default Page<TourGuide> findFiltered(
            String pattern, GuideStatus status, Long userId, Pageable pageable) {
        Specification<TourGuide> spec =
                (root, query, cb) -> {
                    var p = new ArrayList<Predicate>();
                    if (pattern != null)
                        p.add(cb.like(cb.lower(root.get("specialization")), pattern, '!'));
                    if (status != null) p.add(cb.equal(root.get("status"), status));
                    if (userId != null) p.add(cb.equal(root.get("userId"), userId));
                    return cb.and(p.toArray(Predicate[]::new));
                };
        return findAll(spec, pageable);
    }
}
