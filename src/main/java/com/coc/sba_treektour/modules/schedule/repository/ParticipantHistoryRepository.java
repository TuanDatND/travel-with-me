package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.ParticipantHistory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantHistoryRepository extends JpaRepository<ParticipantHistory, Long> {
    List<ParticipantHistory> findByParticipantIdOrderByIdDesc(Long participantId, Pageable pageable);
}
