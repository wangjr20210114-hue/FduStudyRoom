package com.campus.resource.api;
import com.campus.resource.application.ResourceStore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Fixed action and status only; no body, query, check-in code or credentials. */
@Component @Order(-101) public class ResourceFailureAudit extends OncePerRequestFilter {
    private final ResourceStore s;
    public ResourceFailureAudit(ResourceStore s){
        this.s=s;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest r){
        return !r.getRequestURI().startsWith("/api/resources/")&&!r.getRequestURI().startsWith("/internal/resources/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        try{
            chain.doFilter(request,response);
        }
        finally{
            if(response.getStatus()>=400){
                UUID id=UUID.randomUUID();
                var data=ResourceStore.map("status",String.valueOf(response.getStatus()),"enabled",false,"requestTraceId",request.getAttribute("traceId"));
                s.transaction(()->{
                    s.event(id,"SECURITY",id,1,"DENIED",null,s.fingerprint(data),data,s.now(),false);return null;
                });
            }
        }
    }
}
