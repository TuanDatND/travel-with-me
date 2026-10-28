package com.coc.sba_treektour.modules.schedule.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.GuideScheduleResponse;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guide-assignments")
@RequiredArgsConstructor
@Tag(name = "Guide assignments")
public class GuideAssignmentController {
    private final ScheduleService scheduleService;

    @GetMapping
    @Operation(summary = "List schedules assigned to the current guide")
    public ApiResponse<List<GuideScheduleResponse>> findAll(
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lịch được phân công của bạn", scheduleService.findAssigned(status, page, size));
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "Get a schedule assigned to the current guide")
    public ApiResponse<GuideScheduleResponse> findById(@PathVariable Long scheduleId) {
        return ApiResponse.success("Chi tiết lịch được phân công", scheduleService.findAssignedById(scheduleId));
    }
}
