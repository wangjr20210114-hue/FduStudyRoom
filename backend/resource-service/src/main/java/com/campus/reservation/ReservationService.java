package com.campus.reservation;

import com.campus.common.error.ApiException;
import com.campus.common.security.*;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import static com.campus.reservation.ReservationStore.*;

@Service
public class ReservationService {
 private final ReservationStore s;
 private final ReservationGateway remote;
 private final Clock clock;
 private final String key;
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 private static final Set<String> ACTIVE=Set.of("PENDING","RESERVED","CHECKED_IN");
 public record Write(Map<String,Object> reservation,boolean duplicate){}
 public ReservationService(DataSource ds,@Qualifier("reservationReminderClock") Clock clock,AuthSettings auth,Environment env,
   @Value("${campus.reservation.notification-url:http://workflow:8081}") String notifications,
   @Value("${campus.reservation.resource-url:}") String resources){
  this.s=new ReservationStore(ds);this.clock=clock;this.key=auth.serviceKey();
  this.remote=new ReservationGateway(()->URI.create("http://127.0.0.1:"+env.getProperty("local.server.port",env.getProperty("server.port","8080"))),resources,notifications,key);
 }
 @PreDestroy void close(){remote.close();}
 private UUID uuid(Object value){try{return value instanceof UUID u?u:UUID.fromString(value.toString());}catch(Exception e){throw new ApiException(400,"INVALID_RESERVATION_INPUT");}}
 private int number(Map<String,Object>m,String key){Object v=m.get(key);if(!(v instanceof Number n)||n.intValue()<=0)throw new ApiException(503,"INVALID_TRUSTED_SETTINGS");return n.intValue();}
 private void owner(Map<String,Object>r,IdentityPrincipal actor){if(!actor.id().equals(r.get("user_id")))throw new ApiException(403,"RESERVATION_OWNER_REQUIRED");}
 private String fingerprint(UUID actor,String action,Object body){return s.hash(map("actor",actor.toString(),"action",action,"input",body));}
 private boolean active(Map<String,Object>r){return ACTIVE.contains(r.get("status"));}
 private void interval(Instant start,Instant end,boolean hours,Map<String,Object>policy){if(start==null||end==null||!end.isAfter(start)||start.isBefore(clock.instant()))throw new ApiException(400,"INVALID_RESERVATION_TIME");if(hours&&(start.atZone(ZONE).getMinute()!=0||start.atZone(ZONE).getSecond()!=0||start.getNano()!=0||end.atZone(ZONE).getMinute()!=0||end.atZone(ZONE).getSecond()!=0||end.getNano()!=0||Duration.between(start,end).compareTo(Duration.ofHours(number(policy,"maxReservationHours")))>0))throw new ApiException(400,"INVALID_RESERVATION_DURATION");}
 public List<Map<String,Object>> availability(IdentityPrincipal actor,Instant start,Instant end,String campus,String building,Boolean socket,UUID roomId){
  interval(start,end,false,null);String query="/internal/resources/availability?startsAt="+ReservationGateway.enc(start)+"&endsAt="+ReservationGateway.enc(end)+"&userId="+actor.id();
  if(campus!=null)query+="&campus="+ReservationGateway.enc(campus);if(building!=null)query+="&building="+ReservationGateway.enc(building);if(socket!=null)query+="&hasSocket="+socket;
  var rows=remote.required(remote.resource(query));if(!rows.isArray())throw new ApiException(503,"INVALID_RESOURCE_RESPONSE");
  List<Map<String,Object>>result=new ArrayList<>();Map<UUID,Map<String,Object>>rooms=new HashMap<>();
  if(!rows.isEmpty()){var catalogue=remote.required(remote.resource("/internal/resources/rooms"));if(!catalogue.isArray())throw new ApiException(503,"INVALID_RESOURCE_RESPONSE");for(var entry:catalogue){var room=remote.object(entry);rooms.put(uuid(room.get("id")),room);}}
  for(var n:rows){Map<String,Object>seat=remote.object(n);UUID r=uuid(seat.get("roomId"));if(roomId!=null&&!roomId.equals(r))continue;Map<String,Object>room=rooms.get(r);if(room==null)throw new ApiException(503,"RESOURCE_CATALOGUE_INCONSISTENT");Map<String,Object>item=new LinkedHashMap<>(seat);item.put("roomName",room.get("name"));item.put("roomCode",room.get("code"));item.put("campus",room.get("campus"));item.put("building",room.get("building"));result.add(item);}return result;
 }
 public Write create(IdentityPrincipal actor,UUID operation,UUID seatId,Instant start,Instant end){
  String fp=fingerprint(actor.id(),"CREATE",map("seatId",seatId,"startsAt",start,"endsAt",end));
  UUID old=s.command(operation,actor.id(),"CREATE",fp);if(old!=null)return new Write(s.view(s.row(old,false)),true);
  Map<String,Object>policy=remote.settings();for(String k:List.of("version","maxReservationHours","beforeStartReminderMinutes","afterStartReminderMinutes","checkinDeadlineMinutes","approvalTtlMinutes","repairSlaMinutes"))number(policy,k);
  if(number(policy,"afterStartReminderMinutes")>=number(policy,"checkinDeadlineMinutes"))throw new ApiException(503,"INVALID_TRUSTED_SETTINGS");
  interval(start,end,true,policy);
  var eligibility=remote.eligibility(actor.id());if(!Boolean.TRUE.equals(eligibility.get("enabled"))||!(eligibility.get("permissions") instanceof Collection<?>p)||!p.contains("reservation:create"))throw new ApiException(403,"RESERVATION_INELIGIBLE");
  var seat=remote.seat(seatId);UUID roomId=uuid(seat.get("roomId"));var room=remote.room(roomId);
  if(!seatId.equals(uuid(seat.get("id")))||!Boolean.TRUE.equals(seat.get("enabled"))||!Boolean.TRUE.equals(room.get("enabled"))||!Boolean.TRUE.equals(room.get("bookingEnabled")))throw new ApiException(409,"RESOURCE_UNAVAILABLE");
  UUID[] result=s.transaction(()->{
   s.lock(operation.toString());UUID repeated=s.command(operation,actor.id(),"CREATE",fp);if(repeated!=null)return new UUID[]{repeated,null};
   Instant now=clock.instant();interval(start,end,true,policy);UUID id=UUID.randomUUID();
   s.db.update("INSERT INTO m1.seat_reservations(id,user_id,seat_id,room_id,starts_at,ends_at,status,checkin_deadline_at,policy_snapshot,room_snapshot,seat_snapshot,resource_state,created_at,updated_at) VALUES(?,?,?,?,?,?,'PENDING',?,?::jsonb,?::jsonb,?::jsonb,'PENDING',?,?)",id,actor.id(),seatId,roomId,ts(start),ts(end),ts(start.plusSeconds(number(policy,"checkinDeadlineMinutes")*60L)),s.text(policy),s.text(room),s.text(seat),ts(now),ts(now));
   s.command(operation,id,actor.id(),"CREATE",fp,now);
   UUID allocation=event(id,"ALLOCATE",1);s.enqueue(allocation,id,"ALLOCATE",map("operationId",allocation,"sourceModule","M1","sourceId",id,"sourceVersion",1,"roomId",roomId,"seatId",seatId,"userId",actor.id(),"startsAt",start.toString(),"endsAt",end.toString()),true,null,now,now);
   audit(s.row(id,false),actor.id(),"RESERVATION_REQUESTED",true,now);return new UUID[]{id,allocation};
  });
  if(result[1]!=null)dispatch(result[1]);Map<String,Object>row=s.row(result[0],false);
  if("REJECTED".equals(row.get("status")))throw new ApiException(409,"RESERVATION_RESOURCE_CONFLICT");
  return new Write(s.view(row),result[1]==null);
 }
 public Map<String,Object> detail(IdentityPrincipal actor,UUID id){var r=s.row(id,false);owner(r,actor);var result=s.releaseView(r);var deliveries=s.db.queryForList("SELECT id,status,response::text AS response FROM m1.reservation_outbox WHERE reservation_id=? AND kind='NOTIFY' ORDER BY created_at,id",id);result.put("notifications",deliveries.stream().map(x->map("eventId",x.get("id"),"status",x.get("status"),"receipt",s.parse(x.get("response")))).toList());return result;}
 public Write cancel(IdentityPrincipal actor,UUID id,UUID operation,String reason,boolean administrator){
  if(administrator&&(reason==null||reason.isBlank()||reason.length()>500))throw new ApiException(400,"ADMIN_CANCEL_REASON_REQUIRED");
  String fp=fingerprint(actor.id(),administrator?"ADMIN_CANCEL":"CANCEL",map("id",id,"reason",administrator?reason:null));String action=administrator?"ADMIN_CANCEL":"CANCEL";
  boolean duplicate=s.transaction(()->{
   s.lock(operation.toString());Map<String,Object>r=s.row(id,true);if(!administrator)owner(r,actor);
   if(s.command(operation,actor.id(),action,fp)!=null)return true;
   if(Set.of("CANCELLED","NO_SHOW","CANCEL_PENDING").contains(r.get("status"))){s.command(operation,id,actor.id(),action,fp,clock.instant());return true;}
   if(!active(r)||(administrator&&!clock.instant().isBefore(instant(r.get("ends_at"))))||(!administrator&&(!"RESERVED".equals(r.get("status"))||!clock.instant().isBefore(instant(r.get("starts_at"))))))throw new ApiException(409,"RESERVATION_CANNOT_CANCEL");
   terminate(r,administrator?"ADMIN":"USER","CANCELLED",actor.id(),clock.instant());s.command(operation,id,actor.id(),action,fp,clock.instant());return false;
  });
  dispatch(event(id,"RELEASE",((Number)s.row(id,false).get("resource_version")).intValue()));flushFor(id);return new Write(s.releaseView(s.row(id,false)),duplicate);
 }
 public Write checkin(IdentityPrincipal actor,UUID id,UUID operation,String code){
  Map<String,Object>r=s.row(id,false);owner(r,actor);if(code==null||!code.matches("[0-9]{6}"))throw new ApiException(400,"INVALID_CHECKIN_CODE");
  // Store a digest rather than the room secret in operation persistence.
  String fp=fingerprint(actor.id(),"CHECKIN",map("id",id,"codeHash",s.hash(code)));
  if(s.command(operation,actor.id(),"CHECKIN",fp)!=null)return new Write(s.view(r),true);
  checkinWindow(r,clock.instant());if(r.get("room_id")==null)throw new ApiException(409,"LEGACY_ROOM_METADATA_UNAVAILABLE");
  var verification=remote.request(remote.resource("/internal/resources/checkin-codes/verify"),"POST",map("roomId",r.get("room_id"),"date",clock.instant().atZone(ZONE).toLocalDate().toString(),"code",code));
  if(verification.status()!=200)throw new ApiException(verification.status()>=500?503:verification.status(),"CHECKIN_CODE_SERVICE_UNAVAILABLE");if(!verification.body().path("valid").asBoolean())throw new ApiException(400,"INVALID_CHECKIN_CODE");
  boolean duplicate=s.transaction(()->{s.lock(operation.toString());var current=s.row(id,true);owner(current,actor);if(s.command(operation,actor.id(),"CHECKIN",fp)!=null)return true;Instant now=clock.instant();checkinWindow(current,now);
   s.db.update("UPDATE m1.seat_reservations SET status='CHECKED_IN',checked_in_at=?,version=version+1,updated_at=? WHERE id=?",ts(now),ts(now),id);cancelReminders(current,now);s.command(operation,id,actor.id(),"CHECKIN",fp,now);audit(s.row(id,false),actor.id(),"RESERVATION_CHECKED_IN",true,now);return false;});
  flushFor(id);return new Write(s.view(s.row(id,false)),duplicate);
 }
 private void checkinWindow(Map<String,Object>r,Instant now){if(!"RESERVED".equals(r.get("status"))||now.isBefore(instant(r.get("starts_at")))||!now.isBefore(instant(r.get("checkin_deadline_at")))||!now.isBefore(instant(r.get("ends_at"))))throw new ApiException(409,"CHECKIN_WINDOW_CLOSED");}
 private UUID event(UUID id,String kind,int version){return UUID.nameUUIDFromBytes(("M03:"+id+":"+kind+":"+version).getBytes(StandardCharsets.UTF_8));}
 private String requestTrace(){var a=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();if(a instanceof org.springframework.web.context.request.ServletRequestAttributes request&&request.getRequest().getAttribute("traceId") instanceof String value&&value.matches("[A-Za-z0-9_.:-]{1,64}"))return value;return UUID.randomUUID().toString();}
 private void audit(Map<String,Object>r,UUID actor,String action,boolean success,Instant now){UUID id=(UUID)r.get("id");UUID e=UUID.randomUUID();Map<String,Object>after=map("status",r.get("status"),"version",r.get("version"));if(r.get("room_id")!=null)after.put("roomId",r.get("room_id").toString());if(r.get("cancel_reason")!=null)after.put("reasonCode",r.get("cancel_reason"));s.enqueue(e,id,"AUDIT",map("eventKey",e.toString(),"sourceService","resource-service","actorId",actor,"action",action,"sourceModule","M1","sourceId",id,"success",success,"occurredAt",now.toString(),"traceId",requestTrace(),"beforeData",Map.of(),"afterData",after),true,null,now,now);}
 public void denied(UUID actor,int status,String trace){Instant now=clock.instant();UUID event=UUID.randomUUID();s.transaction(()->{s.enqueue(event,null,"AUDIT",map("eventKey",event.toString(),"sourceService","resource-service","actorId",actor,"action","RESERVATION_ACCESS_DENIED","sourceModule","M1","sourceId",null,"success",false,"occurredAt",now.toString(),"traceId",trace==null?event.toString():trace,"beforeData",Map.of(),"afterData",map("status",Integer.toString(status))),true,null,now,now);return null;});}
 private void notify(Map<String,Object>r,String kind,Instant due,Instant now){UUID id=(UUID)r.get("id"),event=event(id,"NOTIFY_"+kind,1);var room=s.parse(r.get("room_snapshot"));var seat=s.parse(r.get("seat_snapshot"));String location=Objects.toString(room.get("name"),Objects.toString(r.get("room_id"),""))+" / "+Objects.toString(seat.get("code"),Objects.toString(r.get("seat_id"),""));
  String when=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE).format(instant(r.get("starts_at")))+"–"+DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE).format(instant(r.get("ends_at")))+" Asia/Shanghai";
  String title=kind.equals("BEFORE")?"预约提醒":kind.equals("AFTER")?"签到提醒":"预约已取消";
  String titleEn=kind.equals("BEFORE")?"Booking reminder":kind.equals("AFTER")?"Check-in reminder":"Reservation cancelled";
  String zh=kind.equals("BEFORE")?"您的预约即将开始，请按时签到。":kind.equals("AFTER")?"您的预约尚未签到，请及时签到。":"您的预约已取消，原因："+Objects.toString(r.get("cancel_reason"),"")+"。";
  String en=kind.equals("BEFORE")?"Your reservation starts soon. Please check in on time.":kind.equals("AFTER")?"You have not checked in for your reservation. Please check in.":"Your reservation was cancelled. Reason: "+Objects.toString(r.get("cancel_reason"),"")+".";
  s.enqueue(event,id,"NOTIFY",map("eventId",event,"recipientId",r.get("user_id"),"sourceModule","M1","sourceId",id,"title",title,"body",zh+" "+location+" "+when,"titleEn",titleEn,"bodyEn",en+" "+location+" "+when,"scheduledAt",due.toString()),false,Set.of("BEFORE","AFTER").contains(kind)?kind:null,due,now);
 }
 private void terminate(Map<String,Object>r,String reason,String terminal,UUID actor,Instant now){UUID id=(UUID)r.get("id");int version=((Number)r.get("resource_version")).intValue()+1;
  s.db.update("UPDATE m1.seat_reservations SET status='CANCEL_PENDING',terminal_status=?,cancel_reason=?,cancelled_at=?,resource_state='RELEASE_PENDING',resource_version=?,version=version+1,updated_at=? WHERE id=?",terminal,reason,ts(now),version,ts(now),id);
  if(terminal.equals("NO_SHOW"))s.db.update("INSERT INTO m1.reservation_violations(reservation_id,user_id,occurred_at,reason) VALUES(?,?,?,'NO_SHOW') ON CONFLICT(reservation_id) DO NOTHING",id,r.get("user_id"),ts(now));
  UUID release=event(id,"RELEASE",version);s.enqueue(release,id,"RELEASE",map("operationId",release,"sourceModule","M1","sourceId",id,"sourceVersion",version),true,null,now,now);cancelReminders(r,now);Map<String,Object>current=s.row(id,false);notify(current,"CANCEL",now,now);audit(current,actor,"RESERVATION_CANCELLED",true,now);
 }
 private void cancelReminders(Map<String,Object>r,Instant now){UUID id=(UUID)r.get("id");var jobs=s.db.queryForList("SELECT * FROM m1.reservation_outbox WHERE reservation_id=? AND reminder_kind IS NOT NULL AND status<>'CANCELLED'",id);
  for(var job:jobs){s.db.update("UPDATE m1.reservation_outbox SET status='CANCELLED',claim_id=NULL WHERE id=?",job.get("id"));if(((Number)job.get("attempts")).intValue()>0){UUID cancel=event((UUID)job.get("id"),"CANCEL",1);s.enqueue(cancel,id,"NOTIFY_CANCEL",map("eventId",job.get("id")),true,null,now,now);}}
 }
 @Scheduled(fixedDelayString="${campus.reservation.lifecycle-delay-ms:5000}")
 public void tick(){
  Instant now=clock.instant();
  // Deadline decisions are durable before any overdue reminder is prepared.
  for(var item:s.db.queryForList("SELECT id FROM m1.seat_reservations WHERE room_id IS NOT NULL AND status IN ('PENDING','RESERVED','CHECKED_IN') ORDER BY checkin_deadline_at,id LIMIT 1000")){
   UUID id=(UUID)item.get("id");s.transaction(()->{var r=s.row(id,true);if(!active(r))return null;String status=(String)r.get("status");
    if(!"CHECKED_IN".equals(status)&&!now.isBefore(instant(r.get("checkin_deadline_at")))){boolean confirmed=r.get("confirmed_at")!=null;terminate(r,confirmed?"NO_SHOW":"SYSTEM_TIMEOUT",confirmed?"NO_SHOW":"CANCELLED",null,now);return null;}
    if("CHECKED_IN".equals(status)&&!now.isBefore(instant(r.get("ends_at")))){s.db.update("UPDATE m1.seat_reservations SET status='COMPLETED',version=version+1,updated_at=? WHERE id=?",ts(now),id);audit(s.row(id,false),null,"RESERVATION_COMPLETED",true,now);return null;}
    if(!"RESERVED".equals(status))return null;if(!now.isBefore(instant(r.get("ends_at")))){cancelReminders(r,now);return null;}var policy=s.parse(r.get("policy_snapshot"));
    for(String kind:List.of("BEFORE","AFTER")){Instant due=instant(r.get("starts_at")).plusSeconds((kind.equals("BEFORE")?-1L:1L)*number(policy,kind.equals("BEFORE")?"beforeStartReminderMinutes":"afterStartReminderMinutes")*60L);
     if(now.isBefore(due)||(kind.equals("BEFORE")&&!now.isBefore(instant(r.get("starts_at")))))continue;
     UUID event=event(id,"NOTIFY_"+kind,1);int inserted=s.db.update("INSERT INTO m1.reservation_reminders(reservation_id,kind,event_id,due_at) VALUES(?,?,?,?) ON CONFLICT(reservation_id,kind) DO NOTHING",id,kind,event,ts(due));if(inserted==1)notify(r,kind,due,now);
    }return null;
   });
  }
  for(int i=0;i<300;i++){var candidates=s.db.queryForList("SELECT id FROM m1.reservation_outbox WHERE (status='PENDING' OR (status='CLAIMED' AND lease_until<=?)) AND next_retry_at<=? ORDER BY CASE kind WHEN 'RELEASE' THEN 0 WHEN 'ALLOCATE' THEN 1 ELSE 2 END,created_at,id LIMIT 1",ts(clock.instant()),ts(clock.instant()));if(candidates.isEmpty())break;dispatch((UUID)candidates.getFirst().get("id"));}
 }
 private void flushFor(UUID id){for(var r:s.db.queryForList("SELECT id FROM m1.reservation_outbox WHERE reservation_id=? AND status='PENDING' AND next_retry_at<=? ORDER BY created_at,id",id,ts(clock.instant())))dispatch((UUID)r.get("id"));}
 private void dispatch(UUID event){
  UUID claim=UUID.randomUUID();var job=s.transaction(()->{var rows=s.db.queryForList("SELECT * FROM m1.reservation_outbox WHERE id=? AND next_retry_at<=? AND (status='PENDING' OR (status='CLAIMED' AND lease_until<=?)) FOR UPDATE SKIP LOCKED",event,ts(clock.instant()),ts(clock.instant()));if(rows.isEmpty())return null;var j=rows.getFirst();s.db.update("UPDATE m1.reservation_outbox SET status='CLAIMED',claim_id=?,lease_until=?,attempts=attempts+1 WHERE id=?",claim,ts(clock.instant().plusSeconds(30)),event);return j;});
  if(job==null)return;String kind=(String)job.get("kind");UUID reservation=(UUID)job.get("reservation_id");Map<String,Object>payload=s.parse(job.get("payload"));
  try{
   if("NOTIFY".equals(kind)){
    if(job.get("reminder_kind")!=null){var row=s.row(reservation,false);String reminder=(String)job.get("reminder_kind");boolean obsolete=!"RESERVED".equals(row.get("status"))||!clock.instant().isBefore(instant(row.get("checkin_deadline_at")))||!clock.instant().isBefore(instant(row.get("ends_at")))||(reminder.equals("BEFORE")&&!clock.instant().isBefore(instant(row.get("starts_at"))));if(obsolete){finish(event,claim,"CANCELLED",null,null);return;}}
    if(!Boolean.TRUE.equals(job.get("frozen"))){String email=remote.email(uuid(payload.get("recipientId")));payload=new LinkedHashMap<>(payload);payload.put("emailAddress",email);String frozen=s.text(payload);s.transaction(()->{s.db.update("UPDATE m1.reservation_outbox SET payload=?::jsonb,frozen=true WHERE id=? AND claim_id=? AND status='CLAIMED'",frozen,event,claim);return null;});}
   }
   URI endpoint=switch(kind){case "ALLOCATE"->remote.resource("/internal/resources/allocations");case "RELEASE"->remote.resource("/internal/resources/releases");case "AUDIT"->remote.notifications.resolve("/internal/audit/events");case "NOTIFY_CANCEL"->remote.notifications.resolve("/internal/notifications/events/"+payload.get("eventId")+"/cancel");default->remote.notifications.resolve("/internal/notifications/events");};
   final Map<String,Object>requestPayload=payload;
   var response=remote.request(endpoint,"POST",kind.equals("NOTIFY_CANCEL")?null:requestPayload);
   if(response.status()==200||response.status()==201){String raw=s.text(response.body());s.transaction(()->{
    if(kind.equals("ALLOCATE")){var r=s.row(reservation,true);var n=response.body();boolean confirmed=n.path("active").asBoolean()&&n.path("sourceVersion").asInt()==((Number)requestPayload.get("sourceVersion")).intValue()&&reservation.toString().equals(n.path("sourceId").asText());if(!confirmed)throw new ApiException(503,"INVALID_RESOURCE_CONFIRMATION");
     if("PENDING".equals(r.get("status"))){if(!clock.instant().isBefore(instant(r.get("checkin_deadline_at"))))terminate(r,"SYSTEM_TIMEOUT","CANCELLED",null,clock.instant());else{s.db.update("UPDATE m1.seat_reservations SET status='RESERVED',resource_state='CONFIRMED',resource_receipt=?::jsonb,confirmed_at=?,version=version+1,updated_at=? WHERE id=?",raw,ts(clock.instant()),ts(clock.instant()),reservation);audit(s.row(reservation,false),null,"RESERVATION_CONFIRMED",true,clock.instant());}}
    }else if(kind.equals("RELEASE")){var n=response.body();if(n.path("active").asBoolean()||!reservation.toString().equals(n.path("sourceId").asText())||n.path("sourceVersion").asInt()<((Number)requestPayload.get("sourceVersion")).intValue())throw new ApiException(503,"INVALID_RESOURCE_RELEASE");s.db.update("UPDATE m1.seat_reservations SET status=CASE WHEN status='CANCEL_PENDING' THEN terminal_status ELSE status END,resource_state='RELEASED',resource_receipt=?::jsonb,updated_at=? WHERE id=? AND resource_version<=?",raw,ts(clock.instant()),reservation,n.path("sourceVersion").asInt());}
    s.db.update("UPDATE m1.reservation_outbox SET status='DONE',response=?::jsonb,last_error=NULL,claim_id=NULL,lease_until=NULL WHERE id=? AND claim_id=? AND status='CLAIMED'",raw,event,claim);return null;
   });
   }else if(kind.equals("ALLOCATE")&&Set.of(400,403,404,409).contains(response.status())){s.transaction(()->{var r=s.row(reservation,true);if("PENDING".equals(r.get("status"))){s.db.update("UPDATE m1.seat_reservations SET status='REJECTED',resource_state='REJECTED',failure_code='RESOURCE_CONFLICT',version=version+1,updated_at=? WHERE id=?",ts(clock.instant()),reservation);audit(s.row(reservation,false),null,"RESERVATION_REJECTED",false,clock.instant());}s.db.update("UPDATE m1.reservation_outbox SET status='DONE',response=?::jsonb,claim_id=NULL WHERE id=? AND claim_id=?",s.text(response.body()),event,claim);return null;});
   }else finish(event,claim,"PENDING","DEPENDENCY_HTTP_"+response.status(),null);
  }catch(ApiException unavailable){finish(event,claim,"PENDING",unavailable.code(),null);}catch(RuntimeException unavailable){finish(event,claim,"PENDING","RESERVATION_DELIVERY_UNAVAILABLE",null);}
 }
 private void finish(UUID event,UUID claim,String status,String error,String response){s.db.update("UPDATE m1.reservation_outbox SET status=?,last_error=?,next_retry_at=?,claim_id=NULL,lease_until=NULL WHERE id=? AND claim_id=? AND status='CLAIMED'",status,error,ts(clock.instant().plusSeconds(status.equals("PENDING")?30:0)),event,claim);}

 public Map<String,Object> maintenance(UUID operation,UUID source,int version,UUID room,boolean active,String effectiveAt){
  Instant effective;try{effective=Instant.parse(effectiveAt);}catch(Exception bad){throw new ApiException(400,"INVALID_MAINTENANCE_EVENT");}
  Map<String,Object>request=map("operationId",operation,"sourceId",source,"sourceVersion",version,"roomId",room,"active",active,"effectiveAt",effectiveAt);String fp=s.hash(request);
  var previous=s.db.queryForList("SELECT fingerprint,response::text AS response FROM m1.maintenance_operations WHERE operation_id=?",operation);if(!previous.isEmpty()){if(!fp.equals(previous.getFirst().get("fingerprint")))throw new ApiException(409,"MAINTENANCE_OPERATION_CONFLICT");var answer=new LinkedHashMap<>(s.parse(previous.getFirst().get("response")));answer.put("duplicate",true);return answer;}
  Map<String,Object>actual;try{actual=remote.object(remote.required(remote.resource("/internal/resources/sources/M3/"+source)));}catch(ApiException failure){throw new ApiException(503,"MAINTENANCE_SOURCE_UNVERIFIED");}
  if(!room.equals(uuid(actual.get("roomId")))||!source.equals(uuid(actual.get("sourceId")))||!"M3".equals(actual.get("sourceModule")))throw new ApiException(409,"MAINTENANCE_SOURCE_CONFLICT");int actualVersion=((Number)actual.get("sourceVersion")).intValue();if(actualVersion<version)throw new ApiException(503,"MAINTENANCE_SOURCE_NOT_APPLIED");
  if(actualVersion==version&&(!Objects.equals(active,actual.get("active"))||!effective.equals(Instant.parse(actual.get("effectiveAt").toString()))))throw new ApiException(409,"MAINTENANCE_SOURCE_CONFLICT");
  // Keep the original resource receipt JSON (including sub-microsecond effectiveAt) intact.
  String actualFp=s.hash(map("sourceId",source,"roomId",room,"sourceVersion",actualVersion,"active",actual.get("active"),"effectiveAt",actual.get("effectiveAt")));
  Map<String,Object>answer=s.transaction(()->{
   s.lock("MAINTENANCE:"+source);s.lock("MAINTENANCE_OP:"+operation);
   var oldOp=s.db.queryForList("SELECT fingerprint,response::text AS response FROM m1.maintenance_operations WHERE operation_id=?",operation);if(!oldOp.isEmpty()){if(!fp.equals(oldOp.getFirst().get("fingerprint")))throw new ApiException(409,"MAINTENANCE_OPERATION_CONFLICT");var a=new LinkedHashMap<>(s.parse(oldOp.getFirst().get("response")));a.put("duplicate",true);return a;}
   var rows=s.db.queryForList("SELECT * FROM m1.maintenance_sources WHERE source_id=? FOR UPDATE",source);boolean already=false;
   if(!rows.isEmpty()){var old=rows.getFirst();if(!room.equals(old.get("room_id")))throw new ApiException(409,"MAINTENANCE_ROOM_CONFLICT");int high=((Number)old.get("source_version")).intValue();if(high>actualVersion)throw new ApiException(503,"MAINTENANCE_SOURCE_REGRESSED");if(high==actualVersion){if(!actualFp.equals(old.get("fingerprint")))throw new ApiException(409,"MAINTENANCE_VERSION_CONFLICT");already=true;}}
   if(!already){s.db.update("INSERT INTO m1.maintenance_sources(source_id,room_id,source_version,active,receipt,fingerprint,updated_at) VALUES(?,?,?,?,?::jsonb,?,?) ON CONFLICT(source_id) DO UPDATE SET source_version=excluded.source_version,active=excluded.active,receipt=excluded.receipt,fingerprint=excluded.fingerprint,updated_at=excluded.updated_at",source,room,actualVersion,actual.get("active"),s.text(actual),actualFp,ts(clock.instant()));
    if(Boolean.TRUE.equals(actual.get("active"))){for(var r:s.db.queryForList("SELECT * FROM m1.seat_reservations WHERE room_id=? AND ends_at>? AND status IN ('PENDING','RESERVED','CHECKED_IN') ORDER BY id FOR UPDATE",room,ts(clock.instant())))terminate(r,"SYSTEM_MAINTENANCE","CANCELLED",null,clock.instant());}
   }
   Map<String,Object>result=map("operationId",operation,"duplicate",false,"ignored",already||version<actualVersion,"appliedSourceVersion",actualVersion);
   s.db.update("INSERT INTO m1.maintenance_operations(operation_id,fingerprint,response,created_at) VALUES(?,?,?::jsonb,?)",operation,fp,s.text(result),ts(clock.instant()));return result;
  });
  tick();return answer;
 }
 public Map<String,Object> statistics(Instant from,Instant to,Instant asOf,UUID room){
  if(from==null||to==null||asOf==null||!to.isAfter(from)||asOf.isAfter(clock.instant()))throw new ApiException(400,"INVALID_RESERVATION_STATISTICS_RANGE");
  String where="starts_at>=? AND starts_at<?";List<Object>args=new ArrayList<>(List.of(ts(from),ts(to)));if(room!=null){where+=" AND room_id=?";args.add(room);}
  long checked=0,noShow=0,preCancelled=0,system=0,notDue=0,unevaluated=0,unconfirmed=0;
  for(var r:s.db.queryForList("SELECT * FROM m1.seat_reservations WHERE "+where,args.toArray())){
   Instant cancel=instant(r.get("cancelled_at"));String reason=Objects.toString(r.get("cancel_reason"),"");
   if(cancel!=null&&!cancel.isAfter(asOf)&&Set.of("SYSTEM_MAINTENANCE","REPAIR","SYSTEM_TIMEOUT","EXTERNAL_CONFLICT").contains(reason)){system++;continue;}
   if(cancel!=null&&!cancel.isAfter(asOf)&&cancel.isBefore(instant(r.get("starts_at")))){preCancelled++;continue;}
   if(instant(r.get("checkin_deadline_at")).isAfter(asOf)){notDue++;continue;}
   if(r.get("confirmed_at")==null&&r.get("room_id")!=null){unconfirmed++;continue;}
   Instant signed=instant(r.get("checked_in_at"));if(signed!=null&&!signed.isAfter(asOf)){checked++;continue;}
   Long count=s.db.queryForObject("SELECT count(*) FROM m1.reservation_violations WHERE reservation_id=? AND occurred_at<=?",Long.class,r.get("id"),ts(asOf));if(count!=null&&count>0)noShow++;else if("RESERVED".equals(r.get("status")))unevaluated++;
  }
  return map("from",from,"to",to,"asOf",asOf,"roomId",room,"checkedInCount",checked,"noShowCount",noShow,"excludedBeforeStartCancelledCount",preCancelled,"excludedSystemTerminatedCount",system,"notDueCount",notDue,"pendingEvaluationCount",unevaluated,"excludedUnconfirmedCount",unconfirmed,"algorithm","M1_CONFIRMED_DUE_P08_V1","source","m1.seat_reservations+m1.reservation_violations; startsAt in [from,to), deadline<=asOf; actual persisted outcomes only");
 }
 public Map<String,Object> rebook(IdentityPrincipal actor,UUID id){var r=s.row(id,false);owner(r,actor);var policy=remote.settings();var seat=remote.seat((UUID)r.get("seat_id"));var room=remote.room(uuid(seat.get("roomId")));boolean past=!instant(r.get("starts_at")).isAfter(clock.instant());return map("sourceReservationId",id,"seatId",r.get("seat_id"),"roomId",seat.get("roomId"),"roomName",room.get("name"),"seatCode",seat.get("code"),"policy",policy,"startsAt",past?null:instant(r.get("starts_at")),"endsAt",past?null:instant(r.get("ends_at")),"requiresNewTimeSelection",past,"timeZone","Asia/Shanghai");}
 private String sign(String payload){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 public Map<String,Object> history(IdentityPrincipal actor,boolean administrator,int limit,String cursor,UUID room,UUID seat,Instant from,Instant to){
  if(limit<1||limit>100||(from!=null&&to!=null&&!to.isAfter(from)))throw new ApiException(400,"INVALID_RESERVATION_QUERY");
  String scope=s.hash(map("actor",actor.id(),"admin",administrator,"room",room,"seat",seat,"from",from,"to",to));
  long snapshot;Instant lastStart=null;UUID lastId=null;
  if(cursor==null){Long maximum=s.db.queryForObject("SELECT coalesce(max(record_seq),0) FROM m1.seat_reservations",Long.class);snapshot=maximum==null?0:maximum;}
  else try{String[]parts=cursor.split("\\.",-1);if(parts.length!=2||!MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII),parts[1].getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();var c=s.parse(new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8));if(!scope.equals(c.get("scope")))throw new IllegalArgumentException();snapshot=((Number)c.get("snapshot")).longValue();lastStart=Instant.parse(c.get("start").toString());lastId=UUID.fromString(c.get("id").toString());}catch(Exception invalid){throw new ApiException(400,"INVALID_RESERVATION_CURSOR");}
  String where=" WHERE record_seq<=?";List<Object>args=new ArrayList<>();args.add(snapshot);if(!administrator){where+=" AND user_id=?";args.add(actor.id());}if(room!=null){where+=" AND room_id=?";args.add(room);}if(seat!=null){where+=" AND seat_id=?";args.add(seat);}if(from!=null){where+=" AND starts_at>=?";args.add(ts(from));}if(to!=null){where+=" AND starts_at<?";args.add(ts(to));}if(lastStart!=null){where+=" AND (starts_at,id)<(?,?)";args.add(ts(lastStart));args.add(lastId);}args.add(limit+1);
  var rows=s.db.queryForList("SELECT * FROM m1.seat_reservations"+where+" ORDER BY starts_at DESC,id DESC LIMIT ?",args.toArray());boolean more=rows.size()>limit;if(more)rows=new ArrayList<>(rows.subList(0,limit));
  String next=null;if(more){var last=rows.getLast();String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(s.text(map("scope",scope,"snapshot",snapshot,"start",instant(last.get("starts_at")).toString(),"id",last.get("id").toString())).getBytes(StandardCharsets.UTF_8));next=encoded+"."+sign(encoded);}
  return map("items",s.releaseViews(rows),"nextCursor",next,"limit",limit,"timeZone","Asia/Shanghai");
 }
}
