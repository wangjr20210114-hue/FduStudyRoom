package com.campus.audit;
import com.campus.common.error.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=AuditController.class)
public class AuditAdvice {
 private final ApiErrors errors;public AuditAdvice(ApiErrors errors){this.errors=errors;}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<ApiErrors.Body> unavailable(HttpServletRequest request){return ResponseEntity.status(503).body(errors.body(request,"AUDIT_UNAVAILABLE"));}
}
