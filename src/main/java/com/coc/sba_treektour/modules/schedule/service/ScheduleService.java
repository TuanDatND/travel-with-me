package com.coc.sba_treektour.modules.schedule.service;

import com.coc.sba_treektour.modules.schedule.dto.*;
import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import java.util.List;

public interface ScheduleService {
    ScheduleResponse create(CreateScheduleRequest request);
    ScheduleResponse findById(Long id);
    List<ScheduleResponse> findAll(Long eventId, Long branchId, ScheduleStatus status, int page, int size);
    ScheduleResponse update(Long id, UpdateScheduleRequest request);
    ScheduleResponse reschedule(Long id, RescheduleRequest request);
    ScheduleResponse updateStatus(Long id, UpdateScheduleStatusRequest request);
    List<ScheduleHistoryResponse> getHistories(Long id, int page, int size);
}
