package com.campus.resource.application;
import com.campus.common.security.AuthSettings;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Claim briefly, then release the database connection before network delivery. */
@Component public class ResourceAuditDispatcher {
    private final ResourceStore s;
    private final URI endpoint;
    private final String key;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public ResourceAuditDispatcher(ResourceStore s,AuthSettings settings,@Value("${campus.resource.audit-url:${campus.reservation.notification-url:http://workflow:8081}}")String origin){
        this.s=s;
        this.endpoint=URI.create(origin).resolve("/internal/audit/events");
        this.key=settings.serviceKey();
    }
    @Scheduled(fixedDelayString="${campus.resource.audit-poll-millis:30000}",initialDelayString="${campus.resource.audit-initial-delay-millis:60000}")  public void dispatch(){
        for(int i=0;i<20;i++){
            Map<String,Object> row=s.transaction(()->{
                var rows=s.db.queryForList("SELECT operation_id,audit_payload::text AS payload FROM resource.resource_operations WHERE audit_state='PENDING' AND audit_next<=? ORDER BY audit_next,operation_id LIMIT 1 FOR UPDATE SKIP LOCKED",OffsetDateTime.ofInstant(s.now(),ZoneOffset.UTC));     if(rows.isEmpty())return null;     var item=rows.getFirst();     s.db.update("UPDATE resource.resource_operations SET audit_attempts=audit_attempts+1,audit_next=? WHERE operation_id=?",OffsetDateTime.ofInstant(s.now().plusSeconds(30),ZoneOffset.UTC),item.get("operation_id"));     return item;
            });
            if(row==null)break;
            String state="PENDING";
            try{
                var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("X-Service-Key",key).POST(HttpRequest.BodyPublishers.ofString(row.get("payload").toString())).build();
                int code=http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
                if(code==200||code==201)state="SENT";
                else if(code==400||code==409)state="FAILED";
            }
            catch(InterruptedException e){
                Thread.currentThread().interrupt();
                return;
            }
            catch(Exception ignored){
            }
            s.db.update("UPDATE resource.resource_operations SET audit_state=? WHERE operation_id=? AND audit_state='PENDING'",state,row.get("operation_id"));
        }
    }
}
