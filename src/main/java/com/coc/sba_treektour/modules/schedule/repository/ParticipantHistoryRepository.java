package com.coc.sba_treektour.modules.schedule.repository;
import com.coc.sba_treektour.modules.schedule.entity.ParticipantHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ParticipantHistoryRepository extends JpaRepository<ParticipantHistory, Long> {
    List<ParticipantHistory> findByParticipantIdOrderByIdDesc(Long participantId, Pageable pageable);
}
