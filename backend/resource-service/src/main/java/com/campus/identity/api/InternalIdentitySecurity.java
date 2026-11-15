package com.campus.identity.api;
import com.campus.common.security.AuthSettings;import com.campus.common.error.ApiErrors;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import org.springframework.context.annotation.*;import org.springframework.core.annotation.Order;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;import org.springframework.security.web.SecurityFilterChain;import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;import org.springframework.web.filter.OncePerRequestFilter;
@Configuration(proxyBeanMethods=false)
public class InternalIdentitySecurity {
 @Bean @Order(2) SecurityFilterChain identityInternal(HttpSecurity http,AuthSettings settings,ApiErrors errors)throws Exception{
  return http.securityMatcher("/internal/identity/**","/internal/system/settings").csrf(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable).authorizeHttpRequests(a->a.anyRequest().permitAll()).addFilterBefore(new OncePerRequestFilter(){
   @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String key=request.getHeader("X-Service-Key");if(key==null||!MessageDigest.isEqual(settings.serviceKey().getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8))){errors.write(request,response,401,"SERVICE_AUTHENTICATION_REQUIRED");return;}chain.doFilter(request,response);}
  },UsernamePasswordAuthenticationFilter.class).build();
 }
}
