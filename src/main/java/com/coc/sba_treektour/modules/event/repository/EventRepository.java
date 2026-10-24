package com.coc.sba_treektour.modules.event.repository;

import com.coc.sba_treektour.modules.event.entity.TourEvent;
import com.coc.sba_treektour.modules.event.enums.DifficultyLevel;
import com.coc.sba_treektour.modules.event.enums.EventStatus;
import com.coc.sba_treektour.modules.event.enums.EventType;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

public interface EventRepository
        extends JpaRepository<TourEvent, Long>, JpaSpecificationExecutor<TourEvent> {
    /** Tìm và khóa ghi tour trong giao dịch để tuần tự hóa các thao tác cập nhật. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TourEvent e where e.id = :id")
    Optional<TourEvent> findForUpdate(@Param("id") Long id);

    /** Tạo bộ lọc nội dung/giá/trạng thái tại repository sở hữu tour_events. */
    default Page<TourEvent> findFiltered(
            String pattern,
            EventType type,
            DifficultyLevel difficulty,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            EventStatus status,
            Pageable pageable) {
        Specification<TourEvent> spec =
                (root, query, cb) -> {
                    var predicates = new ArrayList<Predicate>();
                    EventStatus filter = status;
                    if (filter != null) predicates.add(cb.equal(root.get("status"), filter));
                    if (type != null) predicates.add(cb.equal(root.get("eventType"), type));
                    if (pattern != null)
                        predicates.add(cb.like(cb.lower(root.get("eventName")), pattern, '!'));
                    if (difficulty != null)
                        predicates.add(
                                cb.equal(root.join("details").get("difficultyLevel"), difficulty));
                    if (minPrice != null) predicates.add(cb.ge(root.get("basePrice"), minPrice));
                    if (maxPrice != null) predicates.add(cb.le(root.get("basePrice"), maxPrice));
                    return cb.and(predicates.toArray(Predicate[]::new));
                };
        return findAll(spec, pageable);
    }
}
