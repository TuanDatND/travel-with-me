package com.coc.sba_treektour.modules.schedule.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.schedule.dto.request.CreateScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.RescheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.UpdateScheduleRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.UpdateScheduleStatusRequest;
import com.coc.sba_treektour.modules.schedule.dto.response.GuideScheduleResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleHistoryResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ScheduleResponse;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.created("Tạo lịch tour thành công", scheduleService.create(request));
    }

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

    @GetMapping("/my-assignments")
    public ApiResponse<List<GuideScheduleResponse>> myAssignments(
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lịch được phân công của bạn", scheduleService.findAssigned(status, page, size));
    }

    @GetMapping("/my-assignments/{id}")
    public ApiResponse<GuideScheduleResponse> myAssignment(@PathVariable Long id) {
        return ApiResponse.success("Chi tiết lịch được phân công", scheduleService.findAssignedById(id));
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
    public ApiResponse<List<ScheduleHistoryResponse>> getHistories(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lấy lịch sử thay đổi thành công", scheduleService.getHistories(id, page, size));
    }
}
