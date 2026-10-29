package com.campus.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiAdvice {
    private final ApiErrors errors;
    public ApiAdvice(ApiErrors errors) { this.errors = errors; }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrors.Body> handle(ApiException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(errors.body(request, error.code()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiErrors.Body> invalid(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(errors.body(request, "INVALID_REQUEST"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrors.Body> forbidden(AccessDeniedException error, HttpServletRequest request) {
        return ResponseEntity.status(403).body(errors.body(request, "ACCESS_DENIED"));
    }
}
