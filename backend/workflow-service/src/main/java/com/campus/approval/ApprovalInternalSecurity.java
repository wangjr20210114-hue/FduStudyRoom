package com.campus.approval;
import com.campus.common.security.*;import com.campus.common.error.ApiErrors;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import org.springframework.context.annotation.*;import org.springframework.core.annotation.Order;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.web.*;import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;import org.springframework.web.filter.OncePerRequestFilter;
@Configuration public class ApprovalInternalSecurity{
 @Bean @Order(-3) SecurityFilterChain approvalInternal(HttpSecurity http,AuthSettings settings,ApiErrors errors)throws Exception{
 return http.securityMatcher("/internal/approvals/maintenance-events").csrf(c->c.disable()).authorizeHttpRequests(c->c.anyRequest().permitAll()).addFilterBefore(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String supplied=req.getHeader("X-Service-Key");if(supplied==null||!MessageDigest.isEqual(settings.serviceKey().getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){errors.write(req,res,401,"SERVICE_AUTHENTICATION_REQUIRED");return;}chain.doFilter(req,res);}},AnonymousAuthenticationFilter.class).build();
 }
}
