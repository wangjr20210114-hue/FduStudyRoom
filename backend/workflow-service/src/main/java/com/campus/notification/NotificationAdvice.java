package com.campus.notification;

import com.campus.common.error.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=NotificationController.class)
public class NotificationAdvice {
    private final ApiErrors errors;
    public NotificationAdvice(ApiErrors errors){this.errors=errors;}
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrors.Body> databaseUnavailable(DataAccessException error,HttpServletRequest request){return ResponseEntity.status(503).body(errors.body(request,"NOTIFICATION_UNAVAILABLE"));}
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrors.Body> invalidParameter(MethodArgumentTypeMismatchException error,HttpServletRequest request){return ResponseEntity.badRequest().body(errors.body(request,"INVALID_REQUEST"));}
}
