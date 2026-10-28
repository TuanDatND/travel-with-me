package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.SchedulePaymentReview;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulePaymentReviewRepository extends JpaRepository<SchedulePaymentReview, Long> {
    boolean existsByDedupKey(String key);
    List<SchedulePaymentReview> findByParticipantScheduleBranchIdOrderByIdAsc(Long branchId, Pageable page);
    List<SchedulePaymentReview> findAllByOrderByIdAsc(Pageable page);
}
