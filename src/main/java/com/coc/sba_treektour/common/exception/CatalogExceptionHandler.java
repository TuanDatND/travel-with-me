package com.coc.sba_treektour.common.exception;

import com.coc.sba_treektour.common.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.stream.Collectors;

@Order(0)
@RestControllerAdvice(basePackages = {"com.coc.sba_treektour.modules.product.controller", "com.coc.sba_treektour.modules.inventory.controller"})
public class CatalogExceptionHandler {
    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<ApiResponse<Void>> business(CatalogException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.error(exception.status().value(), exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining(", "))
                : "Dữ liệu hoặc tham số không hợp lệ";
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, "Dữ liệu trùng hoặc vi phạm ràng buộc kho"));
    }
}
