package com.coc.sba_treektour.tour.service;

import org.springframework.data.domain.*;
import java.util.Locale;

public final class TourQueries {
    /** Ngăn tạo đối tượng cho lớp chỉ chứa các hàm tiện ích của module tour. */
    private TourQueries() {}

    /** Kiểm tra page không âm, size từ 1–100 rồi tạo yêu cầu phân trang có sắp xếp. */
    public static Pageable page(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100)
            throw TourException.invalid("page must be >= 0 and size between 1 and 100");
        return PageRequest.of(page, size, sort);
    }

    /** Chuẩn hóa tìm kiếm và escape !, %, _ để tìm ký tự thật thay vì wildcard SQL. */
    public static String contains(String input) {
        if (input == null || input.isBlank()) return null;
        return "%"
                + input.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                + "%";
    }
}
