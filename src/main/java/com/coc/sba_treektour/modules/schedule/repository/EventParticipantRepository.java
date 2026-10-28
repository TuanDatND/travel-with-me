package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    /** Đọc ID lịch trước khi khóa để mọi thao tác luôn khóa lịch rồi mới khóa vé. */
    @Query(
            "select p.schedule.id from EventParticipant p where p.id=:id")
    Optional<Long> findScheduleId(@Param("id") Long id);

    /** Khóa vé khi thay đổi trạng thái để tránh cập nhật đồng thời ghi đè nhau. */
    @Lock(
            LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select p from EventParticipant p where p.id=:id")
    Optional<EventParticipant> findForUpdate(
            @Param("id") Long id);

    /** Đọc danh sách vé của lịch. */
    List<EventParticipant> findByScheduleId(Long scheduleId);

    /** Đọc vé mới nhất trước của người dùng. */
    List<EventParticipant> findByUserIdOrderByRegistrationDateDesc(Long userId);

    /** Tìm vé theo cặp lịch/người dùng để kiểm tra trùng hoặc đăng ký lại. */
    Optional<EventParticipant> findByScheduleIdAndUserId(Long scheduleId, Long userId);

    List<EventParticipant> findByScheduleIdOrderByIdAsc(Long scheduleId, Pageable page);
    List<EventParticipant> findByUserIdOrderByRegistrationDateDescIdDesc(Long userId, Pageable page);

    @Query("select distinct p.schedule.id from EventParticipant p where p.participantStatus = com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus.PENDING and p.expiresAt <= :now order by p.schedule.id")
    List<Long> findExpiredScheduleIds(@Param("now") OffsetDateTime now,
            Pageable page);

    @Query(value = """
            SELECT p.* FROM event_participants p JOIN event_schedules s ON s.schedule_id=p.schedule_id
            WHERE p.user_id=:ownerId AND EXISTS (
                SELECT 1 FROM staff st JOIN branches b ON b.branch_id=st.branch_id
                WHERE st.user_id=:staffId AND st.branch_id=s.branch_id AND st.status='ACTIVE' AND b.status='ACTIVE')
            ORDER BY p.registration_date DESC, p.participant_id DESC
            """, nativeQuery = true)
    List<EventParticipant> findVisibleUserTickets(@Param("ownerId") Long ownerId,
            @Param("staffId") Long staffId, Pageable page);
}
