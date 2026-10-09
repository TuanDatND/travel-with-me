package com.coc.sba_treektour.schedule.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.schedule.dto.ParticipantResponse;
import com.coc.sba_treektour.schedule.dto.RegisterParticipantRequest;
import com.coc.sba_treektour.schedule.service.ParticipantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ParticipantController {

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @PostMapping("/api/schedules/{scheduleId}/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParticipantResponse> register(
            @PathVariable Long scheduleId,
            @Valid @RequestBody RegisterParticipantRequest request
    ) {
        return ApiResponse.created("Đăng ký giữ chỗ tour thành công", participantService.register(scheduleId, request));
    }

    @GetMapping("/api/schedules/{scheduleId}/participants")
    public ApiResponse<List<ParticipantResponse>> findBySchedule(@PathVariable Long scheduleId) {
        return ApiResponse.success("Lấy danh sách người tham gia thành công", participantService.findByScheduleId(scheduleId));
    }

    @GetMapping("/api/participants/{id}")
    public ApiResponse<ParticipantResponse> findById(@PathVariable Long id) {
        return ApiResponse.success("Lấy thông tin vé thành công", participantService.findById(id));
    }

    @GetMapping("/api/participants/by-user/{userId}")
    public ApiResponse<List<ParticipantResponse>> findByUserId(@PathVariable Long userId) {
        return ApiResponse.success("Lấy danh sách vé của người dùng thành công", participantService.findByUserId(userId));
    }

    @PostMapping("/api/participants/{id}/cancel")
    public ApiResponse<ParticipantResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success("Hủy đăng ký tour thành công", participantService.cancel(id));
    }

    @PatchMapping("/api/participants/{id}/confirm-payment")
    public ApiResponse<ParticipantResponse> confirmPayment(@PathVariable Long id) {
        return ApiResponse.success("Xác nhận thanh toán vé thành công", participantService.confirmPayment(id));
    }

    @PatchMapping("/api/participants/{id}/check-in")
    public ApiResponse<ParticipantResponse> checkIn(@PathVariable Long id) {
        return ApiResponse.success("Điểm danh khách hàng thành công", participantService.checkIn(id));
    }

    @PatchMapping("/api/participants/{id}/no-show")
    public ApiResponse<ParticipantResponse> markNoShow(@PathVariable Long id) {
        return ApiResponse.success("Đánh dấu vắng mặt thành công", participantService.markNoShow(id));
    }
}
