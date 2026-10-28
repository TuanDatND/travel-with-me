package com.coc.sba_treektour.modules.schedule.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.schedule.dto.request.CreateScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.PatchScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.RescheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.UpdateScheduleStatusRequest;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleHistoryResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleResponse;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Schedules")
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "Create a schedule")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.created("Tạo lịch tour thành công", scheduleService.create(request));
    }

    @Operation(summary = "List schedules")
    @GetMapping
    public ApiResponse<List<ScheduleResponse>> findAll(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.success("Lấy danh sách lịch tour thành công", scheduleService.findAll(eventId, branchId, status, page, size));
    }

    @Operation(summary = "Get a schedule")
    @GetMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponse> findById(@PathVariable Long scheduleId) {
        return ApiResponse.success("Lấy chi tiết lịch tour thành công", scheduleService.findById(scheduleId));
    }

    @Operation(summary = "Update schedule details")
    @PatchMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponse> patch(@PathVariable Long scheduleId,
            @Valid @RequestBody PatchScheduleRequest request) {
        return ApiResponse.success("Cập nhật thông tin lịch tour thành công", scheduleService.patch(scheduleId, request));
    }

    @Operation(summary = "Reschedule a departure")
    @PostMapping("/{scheduleId}/reschedulings")
    public ApiResponse<ScheduleResponse> reschedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody RescheduleRequest request
    ) {
        return ApiResponse.success("Dời ngày tour thành công", scheduleService.reschedule(scheduleId, request));
    }

    @Operation(summary = "Update schedule status")
    @PatchMapping("/{scheduleId}/status")
    public ApiResponse<ScheduleResponse> updateStatus(
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateScheduleStatusRequest request
    ) {
        return ApiResponse.success("Cập nhật trạng thái lịch tour thành công", scheduleService.updateStatus(scheduleId, request));
    }

    @Operation(summary = "List schedule history")
    @GetMapping("/{scheduleId}/history")
    public ApiResponse<List<ScheduleHistoryResponse>> getHistories(@PathVariable Long scheduleId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lấy lịch sử thay đổi thành công", scheduleService.getHistories(scheduleId, page, size));
    }
}
