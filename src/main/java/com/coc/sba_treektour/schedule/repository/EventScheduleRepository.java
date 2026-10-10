package com.coc.sba_treektour.schedule.repository;

import com.coc.sba_treektour.schedule.entity.EventSchedule;
import com.coc.sba_treektour.schedule.entity.ScheduleStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventScheduleRepository extends JpaRepository<EventSchedule, Long> {

    /** Khóa lịch để tuần tự hóa đăng ký, hủy, đổi lịch và phân công. */
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventSchedule s where s.id = :id")
    java.util.Optional<EventSchedule> findForUpdate(@Param("id") Long id);

    /** Kết hợp tất cả bộ lọc; khách chỉ thấy lịch của tour PUBLISHED. */
    @Query(
            value =
                    """
                    SELECT s.* FROM event_schedules s JOIN tour_events e ON e.event_id=s.event_id
                    WHERE (:eventId IS NULL OR s.event_id=:eventId)
                      AND (:branchId IS NULL OR s.branch_id=:branchId)
                      AND (:status IS NULL OR s.status=:status)
                      AND (:manager OR e.status='PUBLISHED')
                    ORDER BY s.start_datetime ASC, s.schedule_id ASC
                    """,
            nativeQuery = true)
    List<EventSchedule> search(
            @Param("eventId") Long eventId,
            @Param("branchId") Long branchId,
            @Param("status") String status,
            @Param("manager") boolean manager);

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
            @Param("startTime") java.time.OffsetDateTime startTime,
            @Param("endTime") java.time.OffsetDateTime endTime);

    /** Đọc lịch theo tour, sắp xếp ngày khởi hành. */
    List<EventSchedule> findByEventIdOrderByStartDatetimeAsc(Long eventId);

    /** Đọc lịch theo chi nhánh. */
    List<EventSchedule> findByBranchIdOrderByStartDatetimeAsc(Long branchId);

    /** Đọc lịch theo trạng thái. */
    List<EventSchedule> findByStatusOrderByStartDatetimeAsc(ScheduleStatus status);

    /** Cập nhật sức chứa và OPEN/FULL trong một câu SQL; chỉ giữ chỗ khi còn chỗ. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE EventSchedule s SET s.currentParticipants = s.currentParticipants + 1,    "
                    + " s.status = CASE WHEN s.currentParticipants + 1 >= s.maxParticipants THEN"
                    + " com.coc.sba_treektour.schedule.entity.ScheduleStatus.FULL ELSE s.status END"
                    + " WHERE s.id = :scheduleId AND s.status ="
                    + " com.coc.sba_treektour.schedule.entity.ScheduleStatus.OPEN AND"
                    + " s.currentParticipants < s.maxParticipants")
    int incrementParticipantsIfAvailable(@Param("scheduleId") Long scheduleId);

    /** Giải phóng một chỗ, chỉ mở lại lịch FULL và không để số chỗ âm. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE EventSchedule s SET s.currentParticipants = s.currentParticipants - 1,    "
                    + " s.status = CASE WHEN s.status ="
                    + " com.coc.sba_treektour.schedule.entity.ScheduleStatus.FULL THEN"
                    + " com.coc.sba_treektour.schedule.entity.ScheduleStatus.OPEN ELSE s.status END"
                    + " WHERE s.id = :scheduleId AND s.currentParticipants > 0")
    int decrementParticipants(@Param("scheduleId") Long scheduleId);
}
