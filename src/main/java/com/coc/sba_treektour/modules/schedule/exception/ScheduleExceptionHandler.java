package com.coc.sba_treektour.modules.schedule.exception;

import com.coc.sba_treektour.common.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-1)
@RestControllerAdvice(basePackages = "com.coc.sba_treektour.modules.schedule")
public class ScheduleExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(RuntimeException ex) {
        return ResponseEntity.status(403).body(ApiResponse.error(403, ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> unauthenticated(RuntimeException ex) {
        return ResponseEntity.status(401).body(ApiResponse.error(401, ex.getMessage()));
    }

    @ExceptionHandler(ScheduleConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(RuntimeException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, ex.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "JSON hoặc tham số không hợp lệ"));
    }

    /** Trả 404 khi không tìm thấy lịch. */
    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleScheduleNotFound(ScheduleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /** Trả 404 khi không tìm thấy vé. */
    @ExceptionHandler(ParticipantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleParticipantNotFound(
            ParticipantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /** Trả 409 khi lịch không còn nhận đăng ký. */
    @ExceptionHandler(ScheduleNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleScheduleNotAvailable(
            ScheduleNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /** Trả 409 khi tài khoản đã có vé chưa hủy. */
    @ExceptionHandler(DuplicateRegistrationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateRegistration(
            DuplicateRegistrationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /** Trả 400 khi thao tác không phù hợp trạng thái. */
    @ExceptionHandler(InvalidScheduleOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOperation(
            InvalidScheduleOperationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /** Trả 400 cùng lỗi đầu vào đầu tiên. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        String errorMsg =
                ex.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(err -> err.getField() + ": " + err.getDefaultMessage())
                        .orElse("Dữ liệu đầu vào không hợp lệ");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }
}
