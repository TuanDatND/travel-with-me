package com.coc.sba_treektour.schedule.repository;

import com.coc.sba_treektour.schedule.entity.EventSchedule;
import com.coc.sba_treektour.schedule.entity.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventScheduleRepository extends JpaRepository<EventSchedule, Long> {

    List<EventSchedule> findByEventIdOrderByStartDatetimeAsc(Long eventId);

    List<EventSchedule> findByBranchIdOrderByStartDatetimeAsc(Long branchId);

    List<EventSchedule> findByStatusOrderByStartDatetimeAsc(ScheduleStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EventSchedule s " +
           "SET s.currentParticipants = s.currentParticipants + 1, " +
           "    s.status = CASE WHEN s.currentParticipants + 1 >= s.maxParticipants THEN com.coc.sba_treektour.schedule.entity.ScheduleStatus.FULL ELSE s.status END " +
           "WHERE s.id = :scheduleId AND s.status = com.coc.sba_treektour.schedule.entity.ScheduleStatus.OPEN AND s.currentParticipants < s.maxParticipants")
    int incrementParticipantsIfAvailable(@Param("scheduleId") Long scheduleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EventSchedule s " +
           "SET s.currentParticipants = s.currentParticipants - 1, " +
           "    s.status = CASE WHEN s.status = com.coc.sba_treektour.schedule.entity.ScheduleStatus.FULL THEN com.coc.sba_treektour.schedule.entity.ScheduleStatus.OPEN ELSE s.status END " +
           "WHERE s.id = :scheduleId AND s.currentParticipants > 0")
    int decrementParticipants(@Param("scheduleId") Long scheduleId);
}
