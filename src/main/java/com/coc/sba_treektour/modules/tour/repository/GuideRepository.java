package com.coc.sba_treektour.modules.tour.repository;

import com.coc.sba_treektour.modules.tour.entity.TourGuide;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

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
}
