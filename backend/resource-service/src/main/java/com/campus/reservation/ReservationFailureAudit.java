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

@Component @Order(-101)
public class ReservationFailureAudit extends OncePerRequestFilter {
 private static final String ACTOR_ATTRIBUTE=ReservationFailureAudit.class.getName()+".actorId";
 private final ReservationService service;
 public ReservationFailureAudit(ReservationService service){this.service=service;}
 private static boolean reservationPath(HttpServletRequest request){String p=request.getRequestURI();return p.startsWith("/api/reservations")||p.startsWith("/api/admin/reservations");}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){return !reservationPath(request);}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
  try{chain.doFilter(request,response);}
  finally{if(response.getStatus()==401||response.getStatus()==403){UUID actor=request.getAttribute(ACTOR_ATTRIBUTE) instanceof UUID value?value:null;service.denied(actor,response.getStatus(),(String)request.getAttribute("traceId"));}}
 }
 /** Captures only the verified principal while Spring Security still holds its context. */
 @Component @Order(-99)
 public static class ActorCapture extends OncePerRequestFilter {
  @Override protected boolean shouldNotFilter(HttpServletRequest request){return !reservationPath(request);}
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
   var auth=SecurityContextHolder.getContext().getAuthentication();
   if(auth!=null&&auth.getPrincipal() instanceof IdentityPrincipal principal)request.setAttribute(ACTOR_ATTRIBUTE,principal.id());
   chain.doFilter(request,response);
  }
 }
}
