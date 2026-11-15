package com.campus.notification;
import com.campus.common.error.ApiErrors;
import com.campus.common.security.AuthSettings;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration(proxyBeanMethods=false)
public class VerificationEmailSecurity {
 @Bean @Order(-1) SecurityFilterChain verificationInternalSecurity(HttpSecurity http,AuthSettings settings,ApiErrors errors)throws Exception {
  http.securityMatcher(request->request.getServletPath().equals("/internal/notifications/verification-email"))
   .csrf(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.anyRequest().permitAll())
   .addFilterBefore(new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
    String key=request.getHeader("X-Service-Key");if(key==null||!MessageDigest.isEqual(settings.serviceKey().getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8))){errors.write(request,response,401,"SERVICE_AUTHENTICATION_REQUIRED");return;}chain.doFilter(request,response);
   }},UsernamePasswordAuthenticationFilter.class);return http.build();
 }
}
