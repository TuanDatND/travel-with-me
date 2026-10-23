package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventScheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventScheduleHistoryRepository extends JpaRepository<EventScheduleHistory, Long> {
    java.util.List<EventScheduleHistory> findByScheduleIdOrderByChangedAtDescIdDesc(Long id, org.springframework.data.domain.Pageable page);

    List<EventScheduleHistory> findByScheduleIdOrderByChangedAtDesc(Long scheduleId);
}
