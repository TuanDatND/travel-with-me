package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data read projection; assignment scope is applied before pagination. */
public interface GuideScheduleRepository extends Repository<EventSchedule, Long> {
    interface Assignment {
        Long getId();
        Long getEventId();
        String getEventName();
        Long getBranchId();
        String getBranchName();
        Long getGuideId();
        Instant getStartDatetime();
        Instant getEndDatetime();
        Integer getMaxParticipants();
        Integer getCurrentParticipants();
        String getStatus();
        Instant getCreatedAt();
        Instant getRegistrationClosesAt();
    }

    @Query(value = """
            SELECT s.schedule_id AS "id", s.event_id AS "eventId", e.event_name AS "eventName",
                   s.branch_id AS "branchId", b.branch_name AS "branchName", s.guide_id AS "guideId",
                   s.start_datetime AS "startDatetime", s.end_datetime AS "endDatetime",
                   s.max_participants AS "maxParticipants", s.current_participants AS "currentParticipants",
                   s.status AS "status", s.created_at AS "createdAt",
                   s.registration_closes_at AS "registrationClosesAt"
            FROM event_schedules s
            JOIN tour_guides g ON g.guide_id=s.guide_id
            JOIN users u ON u.user_id=g.user_id
            JOIN roles r ON r.role_id=u.role_id
            JOIN tour_events e ON e.event_id=s.event_id
            JOIN branches b ON b.branch_id=s.branch_id
            WHERE g.user_id=:userId AND g.status='ACTIVE' AND u.status='ACTIVE' AND r.role_name='GUIDE'
              AND (CAST(:scheduleId AS bigint) IS NULL OR s.schedule_id=:scheduleId)
              AND (CAST(:status AS text) IS NULL OR s.status=:status)
            ORDER BY s.start_datetime ASC, s.schedule_id ASC
            """, nativeQuery = true)
    List<Assignment> findAssigned(@Param("userId") Long userId, @Param("scheduleId") Long scheduleId,
            @Param("status") String status, Pageable page);
}
