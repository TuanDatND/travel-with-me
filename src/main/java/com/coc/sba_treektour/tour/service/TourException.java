package com.coc.sba_treektour.tour.service;

import org.springframework.http.HttpStatus;

public class TourException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /** Khởi tạo lỗi nghiệp vụ cùng HTTP status, mã lỗi và thông báo trả cho client. */
    public TourException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** Trả HTTP status của lỗi nghiệp vụ. */
    public HttpStatus status() {
        return status;
    }

    /** Trả mã lỗi ổn định để client nhận diện loại lỗi. */
    public String code() {
        return code;
    }

    /** Tạo lỗi 404 cho tài nguyên không tồn tại hoặc không được hiển thị công khai. */
    public static TourException missing() {
        return new TourException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    /** Tạo lỗi 409 khi thao tác xung đột với trạng thái hoặc dữ liệu hiện tại. */
    public static TourException conflict(String message) {
        return new TourException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    /** Tạo lỗi 400 cho dữ liệu đầu vào không hợp lệ. */
    public static TourException invalid(String message) {
        return new TourException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }
}
