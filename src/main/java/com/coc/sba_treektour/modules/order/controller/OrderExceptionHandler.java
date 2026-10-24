package com.coc.sba_treektour.modules.order.controller;

import com.coc.sba_treektour.modules.order.exception.InvalidOrderException;
import com.coc.sba_treektour.modules.order.exception.OrderNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@org.springframework.core.annotation.Order(-10)
@RestControllerAdvice(basePackageClasses = OrderController.class)
public class OrderExceptionHandler {
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(org.springframework.security.access.AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
    }
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(InvalidOrderException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidOrderException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
