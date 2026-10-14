package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventScheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventScheduleHistoryRepository extends JpaRepository<EventScheduleHistory, Long> {

    List<EventScheduleHistory> findByScheduleIdOrderByChangedAtDesc(Long scheduleId);
}
