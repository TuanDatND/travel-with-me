package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventScheduleHistory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventScheduleHistoryRepository extends JpaRepository<EventScheduleHistory, Long> {
    List<EventScheduleHistory> findByScheduleIdOrderByChangedAtDescIdDesc(Long id, Pageable page);
}
