package com.coc.sba_treektour.modules.schedule.repository;

import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    /** Đọc ID lịch trước khi khóa để mọi thao tác luôn khóa lịch rồi mới khóa vé. */
    @org.springframework.data.jpa.repository.Query(
            "select p.schedule.id from EventParticipant p where p.id=:id")
    Optional<Long> findScheduleId(@org.springframework.data.repository.query.Param("id") Long id);

    /** Khóa vé khi thay đổi trạng thái để tránh cập nhật đồng thời ghi đè nhau. */
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select p from EventParticipant p where p.id=:id")
    Optional<EventParticipant> findForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    /** Đọc danh sách vé của lịch. */
    List<EventParticipant> findByScheduleId(Long scheduleId);

    /** Đọc vé mới nhất trước của người dùng. */
    List<EventParticipant> findByUserIdOrderByRegistrationDateDesc(Long userId);

    /** Tìm vé theo cặp lịch/người dùng để kiểm tra trùng hoặc đăng ký lại. */
    Optional<EventParticipant> findByScheduleIdAndUserId(Long scheduleId, Long userId);

    List<EventParticipant> findByScheduleIdOrderByIdAsc(Long scheduleId, org.springframework.data.domain.Pageable page);
    List<EventParticipant> findByUserIdOrderByRegistrationDateDescIdDesc(Long userId, org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query("select distinct p.schedule.id from EventParticipant p where p.participantStatus = com.coc.sba_treektour.modules.schedule.entity.ParticipantStatus.PENDING and p.expiresAt <= :now order by p.schedule.id")
    List<Long> findExpiredScheduleIds(@org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now,
            org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT p.* FROM event_participants p JOIN event_schedules s ON s.schedule_id=p.schedule_id
            WHERE p.user_id=:ownerId AND EXISTS (
                SELECT 1 FROM staff st JOIN branches b ON b.branch_id=st.branch_id
                WHERE st.user_id=:staffId AND st.branch_id=s.branch_id AND st.status='ACTIVE' AND b.status='ACTIVE')
            ORDER BY p.registration_date DESC, p.participant_id DESC
            """, nativeQuery = true)
    List<EventParticipant> findVisibleUserTickets(@org.springframework.data.repository.query.Param("ownerId") Long ownerId,
            @org.springframework.data.repository.query.Param("staffId") Long staffId, org.springframework.data.domain.Pageable page);
}
