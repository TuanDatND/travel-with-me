package com.coc.sba_treektour.schedule.repository;

import com.coc.sba_treektour.schedule.entity.EventParticipant;

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
}
