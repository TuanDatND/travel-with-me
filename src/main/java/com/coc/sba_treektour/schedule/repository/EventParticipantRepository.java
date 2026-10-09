package com.coc.sba_treektour.schedule.repository;

import com.coc.sba_treektour.schedule.entity.EventParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    List<EventParticipant> findByScheduleId(Long scheduleId);

    List<EventParticipant> findByUserIdOrderByRegistrationDateDesc(Long userId);

    Optional<EventParticipant> findByScheduleIdAndUserId(Long scheduleId, Long userId);
}
