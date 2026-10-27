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

    public record Account(Long userId, String displayName, boolean active) {}

    /** Đọc tên và trạng thái nhiều tài khoản trong một truy vấn; danh sách rỗng không gọi DB. */
    public Map<Long, Account> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Account> result = new HashMap<>();
        jdbc.query(
                "SELECT user_id, full_name, status FROM users WHERE user_id IN (:ids)",
                Map.of("ids", ids),
                rs -> {
                    Long id = rs.getLong("user_id");
                    result.put(
                            id,
                            new Account(
                                    id,
                                    rs.getString("full_name"),
                                    "ACTIVE".equals(rs.getString("status"))));
                });
        return result;
    }
}
