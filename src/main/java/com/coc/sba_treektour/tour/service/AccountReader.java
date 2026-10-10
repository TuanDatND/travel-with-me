package com.coc.sba_treektour.tour.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Bộ đọc chỉ đọc, dùng đến khi module tài khoản công bố contract dịch vụ ổn định. */
@Component
public class AccountReader {
    private final NamedParameterJdbcTemplate jdbc;

    /** Khởi tạo bộ đọc bằng JDBC; không sửa dữ liệu hay ánh xạ JPA sang module tài khoản. */
    public AccountReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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

    /** Lấy thông tin tối thiểu của tài khoản hoặc báo lỗi 400 nếu userId không tồn tại. */
    public Account find(Long id) {
        Account account = findAll(List.of(id)).get(id);
        if (account == null) throw TourException.invalid("Account does not exist");
        return account;
    }

    /** Yêu cầu tài khoản tồn tại và ACTIVE trước khi tạo hoặc kích hoạt hồ sơ. */
    public Account requireActive(Long id) {
        Account account = find(id);
        if (!account.active()) throw TourException.conflict("Account is inactive");
        return account;
    }
}
