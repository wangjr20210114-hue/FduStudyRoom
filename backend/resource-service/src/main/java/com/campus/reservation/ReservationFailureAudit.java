package com.campus.reservation;
import com.campus.common.security.IdentityPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(-99)
public class ReservationFailureAudit extends OncePerRequestFilter {
 private final ReservationService service;public ReservationFailureAudit(ReservationService service){this.service=service;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){String p=request.getRequestURI();return !p.startsWith("/api/reservations")&&!p.startsWith("/api/admin/reservations");}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{try{chain.doFilter(request,response);}finally{if(response.getStatus()==403){var auth=SecurityContextHolder.getContext().getAuthentication();UUID actor=auth!=null&&auth.getPrincipal() instanceof IdentityPrincipal p?p.id():null;service.denied(actor,response.getStatus(),(String)request.getAttribute("traceId"));}}}
}
