package com.coc.sba_treektour.modules.tour.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Bộ đọc chỉ đọc, dùng đến khi module tài khoản công bố contract dịch vụ ổn định. */
@Repository
@RequiredArgsConstructor
public class AccountReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public record Account(
            Long userId,
            String displayName,
            boolean active,
            boolean activeStaff,
            boolean blocked) {
        public Account(Long userId, String displayName, boolean active) {
            this(userId, displayName, active, false, false);
        }

        public Account(Long userId, String displayName, boolean active, boolean activeStaff) {
            this(userId, displayName, active, activeStaff, false);
        }
    }

    /** Đọc tên và trạng thái nhiều tài khoản trong một truy vấn; danh sách rỗng không gọi DB. */
    public Map<Long, Account> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Account> result = new HashMap<>();
        jdbc.query(
                "SELECT u.user_id, u.full_name, u.status, "
                        + "EXISTS(SELECT 1 FROM staff s WHERE s.user_id = u.user_id AND s.status = 'ACTIVE') as is_active_staff "
                        + "FROM users u WHERE u.user_id IN (:ids)",
                Map.of("ids", ids),
                rs -> {
                    Long id = rs.getLong("user_id");
                    String rawStatus = rs.getString("status");
                    boolean isBlocked = "BLOCKED".equalsIgnoreCase(rawStatus);
                    boolean isActive = "ACTIVE".equalsIgnoreCase(rawStatus);
                    result.put(
                            id,
                            new Account(
                                    id,
                                    rs.getString("full_name"),
                                    isActive,
                                    rs.getBoolean("is_active_staff"),
                                    isBlocked));
                });
        return result;
    }

    /** Đồng bộ role của tài khoản sang GUIDE và kích hoạt tài khoản khi kích hoạt hồ sơ hướng dẫn viên. */
    public void syncGuideRole(Long userId) {
        jdbc.update(
                "UPDATE users SET role_id = (SELECT role_id FROM roles WHERE role_name = 'GUIDE'), "
                        + "status = 'ACTIVE' "
                        + "WHERE user_id = :userId AND status != 'BLOCKED'",
                Map.of("userId", userId));
    }

    /** Khóa tài khoản người dùng nếu vai trò chỉ là GUIDE khi ngừng hoạt động hồ sơ hướng dẫn viên. */
    public void deactivateGuideUser(Long userId) {
        jdbc.update(
                "UPDATE users SET status = 'INACTIVE' "
                        + "WHERE user_id = :userId "
                        + "AND role_id = (SELECT role_id FROM roles WHERE role_name = 'GUIDE') "
                        + "AND status = 'ACTIVE' "
                        + "AND NOT EXISTS (SELECT 1 FROM staff s WHERE s.user_id = :userId AND s.status = 'ACTIVE')",
                Map.of("userId", userId));
    }
}
