package com.coc.sba_treektour.modules.schedule.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.schedule.dto.request.CancelParticipantRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.ConfirmParticipantPaymentRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.RegisterParticipantRequest;
import com.coc.sba_treektour.modules.schedule.dto.request.UpdateAttendanceRequest;
import com.coc.sba_treektour.modules.schedule.dto.response.ParticipantHistoryResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.ParticipantResponse;
import com.coc.sba_treektour.modules.schedule.dto.response.SchedulePaymentReviewResponse;
import com.coc.sba_treektour.modules.schedule.service.ParticipantService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Participants")
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService participantService;

    @Operation(summary = "Register the current user for a schedule")
    @PostMapping("/api/schedules/{scheduleId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParticipantResponse> register(
            @PathVariable Long scheduleId,
            @Valid @RequestBody RegisterParticipantRequest request
    ) {
        return ApiResponse.created("Đăng ký giữ chỗ tour thành công", participantService.register(scheduleId, request));
    }

    @Operation(summary = "List participants of a schedule")
    @GetMapping("/api/schedules/{scheduleId}/participants")
    public ApiResponse<List<ParticipantResponse>> findBySchedule(@PathVariable Long scheduleId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lấy danh sách người tham gia thành công", participantService.findByScheduleId(scheduleId, page, size));
    }

    @Operation(summary = "Get a participant registration")
    @GetMapping("/api/participants/{participantId}")
    public ApiResponse<ParticipantResponse> findById(@PathVariable Long participantId) {
        return ApiResponse.success("Lấy thông tin vé thành công", participantService.findById(participantId));
    }

    @Operation(summary = "List registration tickets; defaults to the current user")
    @GetMapping("/api/participants")
    public ApiResponse<List<ParticipantResponse>> findByUser(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lấy danh sách vé của người dùng thành công", userId == null ? participantService.findMine(page, size) : participantService.findByUserId(userId, page, size));
    }

    @Operation(summary = "Record a manual payment confirmation")
    @PostMapping("/api/participants/{participantId}/manual-payment-confirmations")
    public ApiResponse<ParticipantResponse> confirmManualPayment(@PathVariable Long participantId,
            @Valid @RequestBody ConfirmParticipantPaymentRequest request) {
        return ApiResponse.success("Xác nhận thanh toán vé thành công", participantService.confirmPayment(participantId, request));
    }

    @Operation(summary = "Update participant attendance")
    @PatchMapping("/api/participants/{participantId}/attendance")
    public ApiResponse<ParticipantResponse> attendance(@PathVariable Long participantId,
            @Valid @RequestBody UpdateAttendanceRequest request) {
        return switch (request.status()) {
            case CHECKED_IN -> ApiResponse.success("Điểm danh khách hàng thành công", participantService.checkIn(participantId));
            case NO_SHOW -> ApiResponse.success("Đánh dấu vắng mặt thành công", participantService.markNoShow(participantId));
        };
    }

    @Operation(summary = "Cancel a participant registration")
    @PostMapping("/api/participants/{participantId}/cancellations")
    public ApiResponse<ParticipantResponse> cancel(@PathVariable Long participantId,
            @Valid @RequestBody(required = false) CancelParticipantRequest request) {
        return ApiResponse.success("Hủy đăng ký tour thành công", participantService.cancel(participantId, request));
    }

    @Operation(summary = "List participant registration history")
    @GetMapping("/api/participants/{participantId}/history")
    public ApiResponse<List<ParticipantHistoryResponse>> histories(@PathVariable Long participantId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Lịch sử vé", participantService.getHistories(participantId, page, size));
    }

    @Operation(summary = "List schedule payment reviews")
    @GetMapping("/api/schedule-payment-reviews")
    public ApiResponse<List<SchedulePaymentReviewResponse>> paymentReviews(@RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Yêu cầu đối soát, chưa phải hoàn tiền thành công", participantService.paymentReviews(branchId, page, size));
    }
}
