package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventScheduleRepository extends JpaRepository<EventSchedule, Long> {

    @Query("select s.id from EventSchedule s where s.registrationClosesAt <= :now and s.status in (com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus.OPEN, com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus.FULL) order by s.id")
    List<Long> findDueToClose(@Param("now") OffsetDateTime now, Pageable page);

    /** Khóa lịch để tuần tự hóa đăng ký, hủy, đổi lịch và phân công. */
    @Lock(
            LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventSchedule s where s.id = :id")
    Optional<EventSchedule> findForUpdate(@Param("id") Long id);

    /** Kết hợp tất cả bộ lọc; khách chỉ thấy lịch của tour PUBLISHED. */
    @Query(
            value =
                    """
                    SELECT s.* FROM event_schedules s JOIN tour_events e ON e.event_id=s.event_id
                    WHERE (:eventId IS NULL OR s.event_id=:eventId)
                      AND (:branchId IS NULL OR s.branch_id=:branchId)
                      AND (:status IS NULL OR s.status=:status)
                      AND (:admin OR e.status='PUBLISHED' OR EXISTS (
                        SELECT 1 FROM staff st JOIN branches b ON b.branch_id=st.branch_id
                        WHERE st.user_id=:staffUserId AND st.branch_id=s.branch_id
                          AND st.status='ACTIVE' AND b.status='ACTIVE'))
                    ORDER BY s.start_datetime ASC, s.schedule_id ASC
                    """,
            nativeQuery = true)
    List<EventSchedule> search(
            @Param("eventId") Long eventId,
            @Param("branchId") Long branchId,
            @Param("status") String status,
            @Param("admin") boolean admin,
            @Param("staffUserId") Long staffUserId,
            Pageable page);

    /** Kiểm tra trùng thời gian theo người, kể cả nhiều guideId của cùng userId. */
    @Query(
            value =
                    """
                    SELECT EXISTS(SELECT 1 FROM event_schedules s JOIN tour_guides g ON g.guide_id=s.guide_id
                    WHERE g.user_id=:userId AND (:excludeId IS NULL OR s.schedule_id<>:excludeId)
                      AND s.status NOT IN ('CANCELLED','COMPLETED')
                      AND s.start_datetime<:endTime AND s.end_datetime>:startTime)
                    """,
            nativeQuery = true)
    boolean overlaps(
            @Param("userId") Long userId,
            @Param("excludeId") Long excludeId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime);

    /** Đọc lịch theo tour, sắp xếp ngày khởi hành. */
    List<EventSchedule> findByEventIdOrderByStartDatetimeAsc(Long eventId);

    /** Đọc lịch theo chi nhánh. */
    List<EventSchedule> findByBranchIdOrderByStartDatetimeAsc(Long branchId);

    /** Đọc lịch theo trạng thái. */
    List<EventSchedule> findByStatusOrderByStartDatetimeAsc(ScheduleStatus status);

}
