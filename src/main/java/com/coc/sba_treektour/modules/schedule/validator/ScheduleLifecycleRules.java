package com.coc.sba_treektour.modules.schedule.validator;

import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.exception.InvalidScheduleOperationException;
import java.time.OffsetDateTime;

/** Pure business rules; no HTTP, security context or database access. */
public final class ScheduleLifecycleRules {
    private ScheduleLifecycleRules() {}

    public static void requireEditable(EventSchedule schedule, OffsetDateTime now) {
        if (!now.isBefore(schedule.getStartDatetime()) || schedule.getStatus() == ScheduleStatus.IN_PROGRESS
                || schedule.getStatus() == ScheduleStatus.COMPLETED || schedule.getStatus() == ScheduleStatus.CANCELLED)
            throw new InvalidScheduleOperationException("Chỉ được sửa lịch chưa khởi hành và chưa kết thúc/hủy");
    }

    public static ScheduleStatus transition(EventSchedule s, ScheduleStatus target, OffsetDateTime now) {
        var old = s.getStatus();
        if (target == ScheduleStatus.FULL) throw new InvalidScheduleOperationException("FULL do hệ thống tính từ sức chứa");
        if (old == target) return old;
        if (old == ScheduleStatus.COMPLETED || old == ScheduleStatus.CANCELLED)
            throw new InvalidScheduleOperationException("Lịch đã kết thúc/hủy không thể đổi trạng thái");
        switch (target) {
            case OPEN -> {
                if (old != ScheduleStatus.CLOSED || !now.isBefore(s.getRegistrationClosesAt()))
                    throw new InvalidScheduleOperationException("Chỉ mở lại lịch CLOSED trước hạn đăng ký");
                return s.getCurrentParticipants() >= s.getMaxParticipants() ? ScheduleStatus.FULL : ScheduleStatus.OPEN;
            }
            case CLOSED -> {
                if (old != ScheduleStatus.OPEN && old != ScheduleStatus.FULL)
                    throw new InvalidScheduleOperationException("Chỉ đóng đăng ký từ OPEN/FULL");
            }
            case IN_PROGRESS -> {
                if (old != ScheduleStatus.CLOSED || now.isBefore(s.getStartDatetime()) || !now.isBefore(s.getEndDatetime())
                        || s.getGuideId() == null)
                    throw new InvalidScheduleOperationException("Tour phải CLOSED, có guide và đang trong thời gian tổ chức");
            }
            case COMPLETED -> {
                if (old != ScheduleStatus.IN_PROGRESS || now.isBefore(s.getEndDatetime()))
                    throw new InvalidScheduleOperationException("Chỉ hoàn tất tour IN_PROGRESS sau giờ kết thúc");
            }
            case CANCELLED -> requireEditable(s, now);
            default -> throw new InvalidScheduleOperationException("Chuyển trạng thái không hợp lệ");
        }
        return target;
    }
}
