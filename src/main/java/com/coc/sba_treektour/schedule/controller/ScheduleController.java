package com.coc.sba_treektour.schedule.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.schedule.dto.*;
import com.coc.sba_treektour.schedule.entity.ScheduleStatus;
import com.coc.sba_treektour.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.created("Tạo lịch tour thành công", scheduleService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> findAll(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) ScheduleStatus status
    ) {
        return ApiResponse.success("Lấy danh sách lịch tour thành công", scheduleService.findAll(eventId, branchId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScheduleResponse> findById(@PathVariable Long id) {
        return ApiResponse.success("Lấy chi tiết lịch tour thành công", scheduleService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateScheduleRequest request
    ) {
        return ApiResponse.success("Cập nhật thông tin lịch tour thành công", scheduleService.update(id, request));
    }

    @PostMapping("/{id}/reschedule")
    public ApiResponse<ScheduleResponse> reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request
    ) {
        return ApiResponse.success("Dời ngày tour thành công", scheduleService.reschedule(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ScheduleResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateScheduleStatusRequest request
    ) {
        return ApiResponse.success("Cập nhật trạng thái lịch tour thành công", scheduleService.updateStatus(id, request));
    }

    @GetMapping("/{id}/histories")
    public ApiResponse<List<ScheduleHistoryResponse>> getHistories(@PathVariable Long id) {
        return ApiResponse.success("Lấy lịch sử thay đổi thành công", scheduleService.getHistories(id));
    }
}
