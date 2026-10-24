package com.coc.sba_treektour.modules.tour.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages) {
    /** Chuyển Page của Spring thành response phân trang ổn định cho client. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
