package com.campus.identity.infrastructure;
import com.campus.common.security.*;import java.net.URI;import java.net.http.*;import java.time.*;import java.util.*;
import org.springframework.beans.factory.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.*;import org.springframework.web.context.request.*;
import tools.jackson.databind.json.JsonMapper;
@Component
public class IdentityAuditOutbox {
 private static final JsonMapper JSON=JsonMapper.builder().build();private final JdbcTemplate jdbc;private final Clock clock;private final AuthSettings auth;private final URI endpoint;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public IdentityAuditOutbox(JdbcTemplate jdbc,@Qualifier("identityClock") Clock clock,AuthSettings auth,@Value("${campus.identity.notification-url:${campus.reservation.notification-url:http://workflow:8081/internal/notifications/events}}") String notifications){this.jdbc=jdbc;this.clock=clock;this.auth=auth;this.endpoint=URI.create(notifications).resolve("/internal/audit/events");}
 @Transactional(propagation=Propagation.MANDATORY)
 public void enqueue(String action,UUID sourceId,Map<String,Object> before,Map<String,Object> after){
  var authentication=SecurityContextHolder.getContext().getAuthentication();UUID actor=authentication!=null&&authentication.getPrincipal() instanceof IdentityPrincipal p?p.id():null;
  String trace=UUID.randomUUID().toString();if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request&&request.getRequest().getAttribute("traceId") instanceof String s)trace=s;
  UUID event=UUID.randomUUID();Instant now=clock.instant();Map<String,Object> payload=new LinkedHashMap<>();payload.put("eventKey",event.toString());payload.put("sourceService","resource-service");payload.put("actorId",actor);payload.put("action",action);payload.put("sourceModule","D");payload.put("sourceId",sourceId);payload.put("success",!action.equals("ACCESS_DENIED"));payload.put("occurredAt",now.toString());payload.put("traceId",trace);payload.put("beforeData",before);payload.put("afterData",after);
  jdbc.update("INSERT INTO iam.audit_outbox(event_key,actor_id,action,source_id,payload,created_at,next_attempt_at) VALUES(?,?,?,?,?::jsonb,?,?)",event,actor,action,sourceId,JSON.writeValueAsString(payload),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void denied(UUID actor,String method,String path){enqueue("ACCESS_DENIED",actor,Map.of(),Map.of("method",method,"path",path));}
 @Scheduled(initialDelay=5000,fixedDelay=5000) public void dispatch(){
  var rows=jdbc.queryForList("SELECT event_key,payload::text AS payload FROM iam.audit_outbox WHERE sent_at IS NULL AND next_attempt_at<=? ORDER BY created_at LIMIT 20",java.sql.Timestamp.from(clock.instant()));
  for(var row:rows){boolean accepted=false;try{var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("X-Service-Key",auth.serviceKey()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString((String)row.get("payload"))).build();int code=client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();accepted=code==200||code==201;}catch(InterruptedException ex){Thread.currentThread().interrupt();return;}catch(Exception ignored){}
   if(accepted)jdbc.update("UPDATE iam.audit_outbox SET sent_at=?,last_error=NULL WHERE event_key=? AND sent_at IS NULL",java.sql.Timestamp.from(clock.instant()),row.get("event_key"));else jdbc.update("UPDATE iam.audit_outbox SET attempts=attempts+1,next_attempt_at=?,last_error='AUDIT_UNAVAILABLE' WHERE event_key=? AND sent_at IS NULL",java.sql.Timestamp.from(clock.instant().plusSeconds(60)),row.get("event_key"));
  }
 }
}
