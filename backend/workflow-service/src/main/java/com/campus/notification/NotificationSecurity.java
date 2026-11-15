package com.campus.notification;

import com.campus.common.error.ApiErrors;
import com.campus.common.security.AuthSettings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
public class NotificationSecurity {
    @Bean @Order(0)
    SecurityFilterChain notificationInternalSecurity(HttpSecurity http,AuthSettings settings,ApiErrors errors)throws Exception {
        // Match only these internal notification operations; other internal routes retain the public chain.
        http.securityMatcher(request -> request.getServletPath().equals("/internal/notifications/events")
                || request.getServletPath().matches("/internal/notifications/events/[0-9a-fA-F-]{36}/cancel"))
            .csrf(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth->auth.anyRequest().permitAll())
            .addFilterBefore(new OncePerRequestFilter(){
                @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
                    String supplied=request.getHeader("X-Service-Key");
                    if(supplied==null||!MessageDigest.isEqual(settings.serviceKey().getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){errors.write(request,response,401,"SERVICE_AUTHENTICATION_REQUIRED");return;}
                    chain.doFilter(request,response);
                }
            },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
