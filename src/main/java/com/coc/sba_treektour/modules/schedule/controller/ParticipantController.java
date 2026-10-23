package com.coc.sba_treektour.modules.schedule.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.schedule.dto.ParticipantResponse;
import com.coc.sba_treektour.modules.schedule.dto.RegisterParticipantRequest;
import com.coc.sba_treektour.modules.schedule.dto.CancelParticipantRequest;
import com.coc.sba_treektour.modules.schedule.dto.ConfirmParticipantRequest;
import com.coc.sba_treektour.modules.schedule.dto.ParticipantHistoryResponse;
import com.coc.sba_treektour.modules.schedule.dto.PaymentReviewResponse;
import com.coc.sba_treektour.modules.schedule.service.ParticipantService;
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
    public ApiResponse<List<ParticipantResponse>> findBySchedule(@PathVariable Long scheduleId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lấy danh sách người tham gia thành công", participantService.findByScheduleId(scheduleId, page, size));
    }

    @GetMapping("/api/participants/{id}")
    public ApiResponse<ParticipantResponse> findById(@PathVariable Long id) {
        return ApiResponse.success("Lấy thông tin vé thành công", participantService.findById(id));
    }

    @GetMapping("/api/participants/by-user/{userId}")
    public ApiResponse<List<ParticipantResponse>> findByUserId(@PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lấy danh sách vé của người dùng thành công", participantService.findByUserId(userId, page, size));
    }

    @PostMapping("/api/participants/{id}/cancel")
    public ApiResponse<ParticipantResponse> cancel(@PathVariable Long id,
            @Valid @RequestBody(required = false) CancelParticipantRequest request) {
        return ApiResponse.success("Hủy đăng ký tour thành công", participantService.cancel(id, request));
    }

    @PatchMapping("/api/participants/{id}/confirm-payment")
    public ApiResponse<ParticipantResponse> confirmPayment(@PathVariable Long id,
            @Valid @RequestBody ConfirmParticipantRequest request) {
        return ApiResponse.success("Xác nhận thanh toán vé thành công", participantService.confirmPayment(id, request));
    }

    @PatchMapping("/api/participants/{id}/check-in")
    public ApiResponse<ParticipantResponse> checkIn(@PathVariable Long id) {
        return ApiResponse.success("Điểm danh khách hàng thành công", participantService.checkIn(id));
    }

    @PatchMapping("/api/participants/{id}/no-show")
    public ApiResponse<ParticipantResponse> markNoShow(@PathVariable Long id) {
        return ApiResponse.success("Đánh dấu vắng mặt thành công", participantService.markNoShow(id));
    }

    @GetMapping("/api/participants/{id}/histories")
    public ApiResponse<List<ParticipantHistoryResponse>> histories(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lịch sử vé", participantService.getHistories(id, page, size));
    }

    @GetMapping("/api/participants/payment-reviews")
    public ApiResponse<List<PaymentReviewResponse>> paymentReviews(@RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Yêu cầu đối soát, chưa phải hoàn tiền thành công", participantService.paymentReviews(branchId, page, size));
    }
}
