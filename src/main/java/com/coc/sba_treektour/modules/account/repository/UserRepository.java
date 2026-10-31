package com.coc.sba_treektour.modules.account.repository;

import com.coc.sba_treektour.modules.account.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM tour_guides WHERE user_id = :userId AND status = 'ACTIVE')", nativeQuery = true)
    boolean hasActiveGuideProfile(@Param("userId") Long userId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM tour_guides WHERE user_id = :userId)", nativeQuery = true)
    boolean hasAnyGuideProfile(@Param("userId") Long userId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM staff WHERE user_id = :userId AND status = 'ACTIVE')", nativeQuery = true)
    boolean hasActiveStaffProfile(@Param("userId") Long userId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM orders WHERE user_id = :userId
                UNION ALL
                SELECT 1 FROM event_participants WHERE user_id = :userId
                UNION ALL
                SELECT 1 FROM staff WHERE user_id = :userId
                UNION ALL
                SELECT 1 FROM tour_guides WHERE user_id = :userId
                UNION ALL
                SELECT 1 FROM transactions WHERE refund_confirmed_by = :userId
            )
            """, nativeQuery = true)
    boolean hasLinkedBusinessRecords(@Param("userId") Long userId);
}
