package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.dto.GuideScheduleResponse;
import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc assignment theo JWT userId, lọc quyền trước khi phân trang. Không map JPA ra module khác. */
@Repository
public class GuideScheduleRepository {
    private final JdbcTemplate jdbc;

    public GuideScheduleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<GuideScheduleResponse> findAssigned(Long userId, Long scheduleId,
            ScheduleStatus status, Pageable page) {
        return jdbc.query("""
                SELECT s.*, e.event_name, b.branch_name
                FROM event_schedules s
                JOIN tour_guides g ON g.guide_id=s.guide_id
                JOIN users u ON u.user_id=g.user_id
                JOIN roles r ON r.role_id=u.role_id
                JOIN tour_events e ON e.event_id=s.event_id
                JOIN branches b ON b.branch_id=s.branch_id
                WHERE g.user_id=? AND g.status='ACTIVE' AND u.status='ACTIVE' AND r.role_name='GUIDE'
                  AND (CAST(? AS bigint) IS NULL OR s.schedule_id=?)
                  AND (CAST(? AS text) IS NULL OR s.status=?)
                ORDER BY s.start_datetime ASC, s.schedule_id ASC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new GuideScheduleResponse(
                        rs.getLong("schedule_id"), rs.getLong("event_id"), rs.getString("event_name"),
                        rs.getLong("branch_id"), rs.getString("branch_name"), rs.getLong("guide_id"),
                        rs.getObject("start_datetime", OffsetDateTime.class),
                        rs.getObject("end_datetime", OffsetDateTime.class),
                        rs.getInt("max_participants"), rs.getInt("current_participants"),
                        ScheduleStatus.valueOf(rs.getString("status")),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("registration_closes_at", OffsetDateTime.class)),
                userId, scheduleId, scheduleId, status == null ? null : status.name(),
                status == null ? null : status.name(), page.getPageSize(), page.getOffset());
    }
}
