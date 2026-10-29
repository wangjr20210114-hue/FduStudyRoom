package com.campus.identity.infrastructure;

import com.campus.common.error.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DatabaseErrors {
    private final ApiErrors errors;
    public DatabaseErrors(ApiErrors errors) { this.errors = errors; }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrors.Body> unavailable(DataAccessException error, HttpServletRequest request) {
        return ResponseEntity.status(503).body(errors.body(request, "IDENTITY_UNAVAILABLE"));
    }
}
