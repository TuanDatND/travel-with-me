package com.coc.sba_treektour.tour.repository;

import com.coc.sba_treektour.tour.entity.TourGuide;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;

public interface GuideRepository
        extends JpaRepository<TourGuide, Long>, JpaSpecificationExecutor<TourGuide> {
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
