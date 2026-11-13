package com.campus.buffer;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class AccessFilter extends OncePerRequestFilter {
 private final BufferSettings settings;
 public AccessFilter(BufferSettings settings){this.settings=settings;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
  if(!request.getRequestURI().startsWith("/api/")){chain.doFilter(request,response);return;}
  var member=settings.authenticate(request.getHeader("Authorization"));
  if(member==null){deny(response,401,"AUTHENTICATION_REQUIRED");return;}
  if(!Set.of("GET","HEAD").contains(request.getMethod())){
   String origin=request.getHeader("Origin");
   if(origin!=null && !settings.allowedOrigins().contains(origin)){deny(response,403,"ORIGIN_NOT_ALLOWED");return;}
   if(request.getContentLengthLong()<0){deny(response,411,"CONTENT_LENGTH_REQUIRED");return;}
   if(request.getContentLengthLong()>8_000_000){deny(response,413,"PAYLOAD_TOO_LARGE");return;}
  }
  request.setAttribute("member",member);response.setHeader("Cache-Control","no-store");chain.doFilter(request,response);
 }
 private void deny(HttpServletResponse response,int status,String code)throws IOException {response.setStatus(status);response.setContentType("application/json");response.getWriter().write(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(java.util.Map.of("code",code)));}
}
