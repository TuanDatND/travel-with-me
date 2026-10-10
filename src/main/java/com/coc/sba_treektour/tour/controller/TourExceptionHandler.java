package com.coc.sba_treektour.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.tour.service.TourException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.OffsetDateTime;
import java.util.*;

// Ưu tiên handler của tour trước handler chung để giữ đúng HTTP status và fieldErrors.
@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(
        basePackages = {
            "com.coc.sba_treektour.tour.controller",
            "com.coc.sba_treektour.event.controller",
            "com.coc.sba_treektour.schedule.controller"
        })
public class TourExceptionHandler {
    /** Tạo ApiResponse chung; data chứa mã lỗi và lỗi theo từng trường để client xử lý. */
    private ResponseEntity<ApiResponse<?>> error(
            HttpStatus status, String code, String message, Map<String, String> fields) {
        // ponytail: tái sử dụng response chung; giữ mã lỗi và lỗi từng trường trong data, không cần
        // DTO lỗi riêng.
        return ResponseEntity.status(status)
                .body(
                        new ApiResponse<>(
                                status.value(),
                                message,
                                Map.of("code", code, "fieldErrors", fields),
                                OffsetDateTime.now()));
    }

    /** Chuyển lỗi nghiệp vụ thành HTTP status và nội dung tương ứng. */
    @ExceptionHandler(TourException.class)
    public ResponseEntity<ApiResponse<?>> business(TourException e) {
        return error(e.status(), e.code(), e.getMessage(), Map.of());
    }

    /** Gom lỗi Bean Validation theo tên trường và trả HTTP 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Request validation failed", fields);
    }

    /** Trả HTTP 400 khi JSON, kiểu tham số hoặc thành phần bắt buộc của request không hợp lệ. */
    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestPartException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<?>> malformed(Exception e) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_INPUT",
                "Invalid request format or parameter",
                Map.of());
    }

    /** Trả HTTP 413 khi file hoặc request multipart vượt giới hạn cấu hình. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> size(MaxUploadSizeExceededException e) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "Maximum image size is 5 MB",
                Map.of());
    }

    /** Trả HTTP 409 khi DB phát hiện trùng dữ liệu hoặc vi phạm ràng buộc tham chiếu. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> constraint(DataIntegrityViolationException e) {
        return error(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "Data conflicts with an existing record or reference",
                Map.of());
    }
}
