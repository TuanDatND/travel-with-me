package com.coc.sba_treektour.tour.repository;

import com.coc.sba_treektour.tour.entity.TourEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EventRepository
        extends JpaRepository<TourEvent, Long>, JpaSpecificationExecutor<TourEvent> {
    /** Tìm và khóa ghi tour trong giao dịch để tuần tự hóa các thao tác cập nhật. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TourEvent e where e.id = :id")
    Optional<TourEvent> findForUpdate(@Param("id") Long id);
}
