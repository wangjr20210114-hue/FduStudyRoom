package com.campus.approval;
import static com.campus.approval.ApprovalEngine.*;
import java.time.*;import java.util.*;import org.springframework.core.env.Environment;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ApprovalJobs {
 final ApprovalEngine e;final boolean scheduling;
 public ApprovalJobs(ApprovalEngine e,Environment env){this.e=e;this.scheduling=env.getProperty("campus.approval.scheduling",Boolean.class,true);}
 @Scheduled(fixedDelay=5000) public void scheduled(){if(scheduling)runOnce();}
 public void runOnce(){e.expire();for(int i=0;i<50;i++){var row=e.tx.execute(s->{var rows=e.db.queryForList("select id,request_id,kind,payload::text as payload,prepared from m2.outbox where state='PENDING' and next_at<=? order by created_at,id limit 1 FOR UPDATE SKIP LOCKED",OffsetDateTime.ofInstant(e.now(),ZoneOffset.UTC));if(rows.isEmpty())return null;var r=rows.getFirst();e.db.update("update m2.outbox set attempts=attempts+1,next_at=? where id=?",OffsetDateTime.ofInstant(e.now().plusSeconds(30),ZoneOffset.UTC),r.get("id"));return r;});if(row==null)return;process(row);}}
 void process(Map<String,Object>job){UUID op=id(job.get("id"));String kind=text(job.get("kind"));Map<String,Object> payload=decode(job.get("payload"));try{
  if(kind.equals("NOTICE")&&!Boolean.TRUE.equals(job.get("prepared"))){var email=e.gateway.require("/internal/identity/users/"+payload.get("recipientId")+"/notification-email");payload.put("emailAddress",Boolean.TRUE.equals(email.get("enabled"))&&Boolean.TRUE.equals(email.get("verified"))?email.get("email"):null);
   int prepared=e.db.update("update m2.outbox set payload=?::jsonb,prepared=true where id=? and not prepared",encode(payload),op);if(prepared==0)payload=decode(e.db.queryForObject("select payload::text from m2.outbox where id=?",String.class,op));}
  ApprovalGateway.Reply response;
  if(kind.equals("ALLOCATE")){
   var r=e.row(id(job.get("request_id")),false);if(Set.of("CANCELLED","REJECTED","EXPIRED").contains(text(r.get("status")))){done(op);return;}
   response=e.gateway.exchange(e.gateway.origin,"/internal/resources/sources/M2/"+payload.get("sourceId"),null);
   Boolean permitted=qualified(r);
   if(!Boolean.TRUE.equals(permitted)){
    boolean knownActive=response.status()==200&&Boolean.TRUE.equals(response.body().get("active"));
    boolean unknownWrite=response.status()!=200&&response.status()!=404;
    if(knownActive||(Boolean.FALSE.equals(permitted)&&unknownWrite))compensate(job,payload);
    else fail(op,permitted==null?"APPROVAL_DEPENDENCY_UNAVAILABLE":"APPROVAL_QUALIFICATION_CHANGED",false);
    return;
   }
   if(response.status()!=200||num(response.body().get("sourceVersion"))!=num(payload.get("sourceVersion"))||!Boolean.TRUE.equals(response.body().get("active")))response=e.gateway.exchange(e.gateway.origin,"/internal/resources/allocations",payload);
  }else if(kind.equals("RELEASE"))response=e.gateway.exchange(e.gateway.origin,"/internal/resources/releases",payload);
  else response=e.gateway.exchange(e.gateway.delivery,kind.equals("NOTICE")?"/internal/notifications/events":"/internal/audit/events",payload);
  if(response.status()==200||response.status()==201){
   if(kind.equals("ALLOCATE")&&!Boolean.TRUE.equals(qualified(e.row(id(job.get("request_id")),false))))compensate(job,payload);
   else if(kind.equals("ALLOCATE")||kind.equals("RELEASE"))resourceAck(job,payload,response.body());else done(op);
  }else{String code=text(response.body().get("code"));boolean permanent=Set.of("IDEMPOTENCY_CONFLICT","SOURCE_VERSION_CONFLICT").contains(code==null?"":code);fail(op,code==null?"HTTP_"+response.status():code,permanent);}
 }catch(Exception error){fail(op,"APPROVAL_DEPENDENCY_UNAVAILABLE",false);}}
 Boolean qualified(Map<String,Object> r){
  try{var user=e.gateway.require("/internal/identity/users/"+r.get("applicantId")+"/eligibility");
   if(!Boolean.TRUE.equals(user.get("enabled"))||!Boolean.TRUE.equals(user.get("canApplyRoom"))||!(user.get("permissions") instanceof List<?> permissions)||!permissions.contains("approval:apply"))return false;
   var room=e.gateway.require("/internal/resources/rooms/"+r.get("roomId"));
   return room.get("departmentCode")==null||Objects.equals(room.get("departmentCode"),user.get("departmentCode"));
  }catch(Exception unavailable){return null;}
 }
 void compensate(Map<String,Object> job,Map<String,Object> payload){e.tx.execute(s->{UUID request=id(job.get("request_id"));var r=e.row(request,true);
  if(num(r.get("resourceVersion"))==num(payload.get("sourceVersion"))&&Set.of("RESOURCE_PENDING","CONFLICT").contains(text(r.get("status")))){
   if(!e.now().isBefore(time(r.get("startsAt")))||!e.now().isBefore(time(r.get("expiresAt"))))e.cancelLocked(r,null,e.now().isBefore(time(r.get("startsAt")))?"EXPIRED":"CANCELLED",e.now().isBefore(time(r.get("startsAt")))?"APPROVAL_TIMEOUT":"START_REACHED");
   else{int version=num(r.get("resourceVersion"))+1;e.db.update("update m2.classroom_requests set resource_state='RELEASE_PENDING',resource_version=?,version=version+1 where id=?",version,request);e.resourceJob(request,"RELEASE",version);e.changed(request,null,"QUALIFICATION_CHANGED",null);}
  }
  e.db.update("update m2.outbox set state='DONE',last_error='APPROVAL_QUALIFICATION_CHANGED' where id=?",job.get("id"));return null;});}
 void resourceAck(Map<String,Object>job,Map<String,Object>payload,Map<String,Object>receipt){UUID op=id(job.get("id")),id=id(job.get("request_id"));if(!Objects.equals(text(receipt.get("sourceId")),id.toString())||num(receipt.get("sourceVersion"))!=num(payload.get("sourceVersion"))||!Objects.equals(receipt.get("sourceModule"),"M2")){fail(op,"INVALID_RESOURCE_RECEIPT",true);return;}
 e.tx.execute(s->{var r=e.row(id,true);boolean allocate=job.get("kind").equals("ALLOCATE");
 if(num(r.get("resourceVersion"))==num(payload.get("sourceVersion"))){
  if(allocate&&Set.of("RESOURCE_PENDING","CONFLICT").contains(text(r.get("status")))&&e.now().isBefore(time(r.get("expiresAt")))&&e.now().isBefore(time(r.get("startsAt")))){
   if(!Boolean.TRUE.equals(receipt.get("active"))||!Objects.equals(text(receipt.get("roomId")),text(r.get("roomId")))||!Objects.equals(time(receipt.get("startsAt")),time(r.get("startsAt")))||!Objects.equals(time(receipt.get("endsAt")),time(r.get("endsAt"))))throw new IllegalStateException("invalid allocation receipt");
   boolean abnormal=Boolean.TRUE.equals(e.db.queryForObject("select exists(select 1 from m2.maintenance_sources where room_id=? and active)",Boolean.class,r.get("roomId")));
   e.db.update("update m2.classroom_requests set status='APPROVED',resource_state='CONFIRMED',resource_abnormal=?,version=version+1 where id=?",abnormal,id);e.changed(id,null,"RESOURCE_CONFIRMED",null);
  }else if(!allocate&&!Boolean.TRUE.equals(receipt.get("active"))){
   if(Set.of("RESOURCE_PENDING","CONFLICT").contains(text(r.get("status")))){
    if(e.now().isBefore(time(r.get("startsAt")))&&e.now().isBefore(time(r.get("expiresAt")))){int next=num(r.get("resourceVersion"))+1;e.db.update("update m2.classroom_requests set resource_state='PENDING',resource_version=?,version=version+1 where id=?",next,id);e.resourceJob(id,"ALLOCATE",next);}
    else e.cancelLocked(r,null,e.now().isBefore(time(r.get("startsAt")))?"EXPIRED":"CANCELLED",e.now().isBefore(time(r.get("startsAt")))?"APPROVAL_TIMEOUT":"START_REACHED");
   }else e.db.update("update m2.classroom_requests set resource_state='RELEASED' where id=?",id);
  }
  else if(allocate&&!Set.of("APPROVED","CANCELLED","EXPIRED","REJECTED").contains(text(r.get("status"))))e.cancelLocked(r,null,e.now().isBefore(time(r.get("startsAt")))?"EXPIRED":"CANCELLED",e.now().isBefore(time(r.get("startsAt")))?"APPROVAL_TIMEOUT":"START_REACHED");
 }
 done(op);return null;});}
 void done(UUID op){e.db.update("update m2.outbox set state='DONE',last_error=null where id=?",op);}
 void fail(UUID op,String code,boolean permanent){e.db.update("update m2.outbox set state=?,last_error=? where id=?",permanent?"ERROR":"PENDING",code,op);}
}
