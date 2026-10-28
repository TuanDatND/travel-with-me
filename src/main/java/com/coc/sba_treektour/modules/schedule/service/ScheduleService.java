package com.coc.sba_treektour.modules.schedule.service;

import com.coc.sba_treektour.modules.schedule.dto.request.CreateScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.PatchScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.RescheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.UpdateScheduleStatusRequest;
import com.coc.sba_treektour.modules.schedule.dto.response.GuideScheduleResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleHistoryResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleResponse;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import java.util.List;

public interface ScheduleService {
    ScheduleResponse create(CreateScheduleRequest request);
    ScheduleResponse findById(Long id);
    List<GuideScheduleResponse> findAssigned(ScheduleStatus status, int page, int size);
    GuideScheduleResponse findAssignedById(Long id);
    List<ScheduleResponse> findAll(Long eventId, Long branchId, ScheduleStatus status, int page, int size);
    ScheduleResponse patch(Long id, PatchScheduleRequest request);
    ScheduleResponse reschedule(Long id, RescheduleRequest request);
    ScheduleResponse updateStatus(Long id, UpdateScheduleStatusRequest request);
    List<ScheduleHistoryResponse> getHistories(Long id, int page, int size);
}
