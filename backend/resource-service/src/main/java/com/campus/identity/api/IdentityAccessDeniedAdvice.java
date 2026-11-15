package com.campus.identity.api;
import com.campus.common.error.ApiErrors;import com.campus.common.security.IdentityPrincipal;import com.campus.identity.infrastructure.IdentityAuditOutbox;import jakarta.servlet.http.*;import org.springframework.core.Ordered;import org.springframework.core.annotation.Order;import org.springframework.security.access.AccessDeniedException;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackages="com.campus.identity.api") @Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityAccessDeniedAdvice {
 private final IdentityAuditOutbox audit;private final ApiErrors errors;public IdentityAccessDeniedAdvice(IdentityAuditOutbox audit,ApiErrors errors){this.audit=audit;this.errors=errors;}
 @ExceptionHandler(AccessDeniedException.class) public void denied(AccessDeniedException exception,HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{var authentication=SecurityContextHolder.getContext().getAuthentication();if(authentication!=null&&authentication.getPrincipal() instanceof IdentityPrincipal p)audit.denied(p.id(),request.getMethod(),request.getServletPath());errors.write(request,response,403,"ACCESS_DENIED");}
}
