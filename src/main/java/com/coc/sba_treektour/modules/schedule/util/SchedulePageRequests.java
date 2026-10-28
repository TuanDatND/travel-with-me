package com.coc.sba_treektour.modules.schedule.util;

import com.coc.sba_treektour.modules.schedule.exception.InvalidScheduleOperationException;
import org.springframework.data.domain.PageRequest;

public final class SchedulePageRequests {
    private SchedulePageRequests() {}
    public static PageRequest of(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidScheduleOperationException("page >= 0; size từ 1 đến 100");
        return PageRequest.of(page, size);
    }
}
