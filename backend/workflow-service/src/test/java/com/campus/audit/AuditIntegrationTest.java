package com.campus.audit;
import com.campus.notification.SupportHttpFixture;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;

class AuditIntegrationTest extends SupportHttpFixture {
 Map<String,Object> event(String trace,boolean success){var e=new LinkedHashMap<String,Object>();e.put("eventKey",UUID.randomUUID().toString());e.put("sourceService","workflow-service");e.put("actorId",user);e.put("action","APPROVAL_DECIDE");e.put("sourceModule","M2");e.put("sourceId",UUID.randomUUID());e.put("success",success);e.put("occurredAt",Instant.now().toString());e.put("traceId",trace);e.put("beforeData",Map.of("status","PENDING","version",1));e.put("afterData",Map.of("status",success?"APPROVED":"DENIED","version",2));return e;}
 void accept(Map<String,Object> e)throws Exception{require(request("POST","/internal/audit/events",e,null,KEY),201);}
 @Test void persistsSuccessAndDeniedWithTrace()throws Exception {
  String trace=UUID.randomUUID().toString();var a=event(trace,true);var b=event(trace,false);accept(a);accept(b);
  var result=get("/api/audit?traceId="+trace);assertEquals(2,result.path("total").asInt());assertEquals(2,result.path("items").size());
  Set<Boolean> successes=new HashSet<>();for(var r:result.path("items")){assertEquals(user.toString(),r.path("actorId").asText());assertEquals("M2",r.path("sourceModule").asText());assertEquals(trace,r.path("traceId").asText());assertFalse(r.path("sourceId").asText().isBlank());assertFalse(r.path("occurredAt").asText().isBlank());successes.add(r.path("success").asBoolean());}assertEquals(Set.of(true,false),successes);
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM support.audit_logs WHERE trace_id=?",Integer.class,trace));
 }
 @Test void projectsSafeSummariesWithoutSecrets(CapturedOutput output)throws Exception {
  String secret="do-not-save-"+UUID.randomUUID();var e=event(UUID.randomUUID().toString(),true);
  e.put("beforeData",Map.of("status","PENDING","password",secret,"Cookie",secret,"signingKey",secret,"request",Map.of("password",secret)));accept(e);
  String stored=jdbc.queryForObject("SELECT row_to_json(a)::text FROM support.audit_logs a WHERE event_key=?",String.class,e.get("eventKey"));assertFalse(stored.contains(secret));assertFalse(stored.contains("password"));assertTrue(stored.contains("PENDING"));assertFalse(output.getAll().contains(secret));
  var result=get("/api/audit?traceId="+e.get("traceId"));assertFalse(result.toString().contains(secret));
 }
 @Test void deduplicatesConflictsAndProtectsQueries()throws Exception {
  String trace=UUID.randomUUID().toString();var e=event(trace,false);accept(e);var again=require(request("POST","/internal/audit/events",e,null,KEY),200);assertTrue(again.path("duplicate").asBoolean());
  try(var pool=Executors.newFixedThreadPool(3)){var futures=new ArrayList<Future<Integer>>();for(int n=0;n<5;n++)futures.add(pool.submit(()->request("POST","/internal/audit/events",e,null,KEY).statusCode()));for(var f:futures)assertEquals(200,f.get());}
  e.put("success",true);require(request("POST","/internal/audit/events",e,null,KEY),409);assertEquals(1,get("/api/audit?traceId="+trace+"&page=0&size=1").path("total").asInt());
  require(request("GET","/api/audit",null,null,null),401);require(request("GET","/api/audit",null,cookie(denied),null),403);permit=false;require(request("GET","/api/audit",null,cookie(user),null),403);permit=true;
  require(request("POST","/internal/audit/events",event(trace,true),cookie(user),null),401);
 }
 @Test void projectsTypedPermissionAndRuleChanges()throws Exception {
  var e=event(UUID.randomUUID().toString(),true);var safe=new LinkedHashMap<String,Object>();safe.put("permissions",List.of("identity:roles:write","audit:read"));safe.put("roleCode","SYSTEM_ADMIN");for(String key:List.of("maxReservationHours","beforeStartReminderMinutes","afterStartReminderMinutes","checkinDeadlineMinutes","approvalTtlMinutes","repairSlaMinutes"))safe.put(key,2);safe.put("hasEmail",true);safe.put("verified",false);safe.put("hasDepartment",true);safe.put("email","never-persist@example.test");e.put("afterData",safe);accept(e);
  var value=get("/api/audit?traceId="+e.get("traceId")).path("items").get(0).path("afterData");assertEquals(2,value.path("permissions").size());assertEquals("SYSTEM_ADMIN",value.path("roleCode").asText());assertEquals(2,value.path("approvalTtlMinutes").asInt());assertTrue(value.path("hasEmail").asBoolean());assertFalse(value.has("email"));
  var bad=event(UUID.randomUUID().toString(),true);bad.put("afterData",Map.of("permissions",List.of("raw secret body with spaces")));require(request("POST","/internal/audit/events",bad,null,KEY),400);
 }
 @Test void validatesQueryAndKeepsAuditAppendOnly()throws Exception {
  var e=event(UUID.randomUUID().toString(),true);accept(e);require(request("GET","/api/audit?page=-1",null,cookie(user),null),400);require(request("GET","/api/audit?size=101",null,cookie(user),null),400);require(request("GET","/api/audit?sourceId=bad",null,cookie(user),null),400);
  assertThrows(DataAccessException.class,()->jdbc.update("UPDATE support.audit_logs SET success=false WHERE event_key=?",e.get("eventKey")));
  assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM support.audit_logs WHERE event_key=?",e.get("eventKey")));
  assertEquals(1,get("/api/audit?sourceModule=M2&sourceId="+e.get("sourceId")).path("total").asInt());
 }

 Map<String,Object> eligibilityEvent(String before,String after){
  var e=event(UUID.randomUUID().toString(),true);e.put("sourceService","resource-service");e.put("sourceModule","D");e.put("action","ELIGIBILITY_CHANGED");
  e.put("beforeData",Map.of("departmentCode",before,"hasDepartment",true));e.put("afterData",Map.of("departmentCode",after,"hasDepartment",true));return e;
 }
 void assertDepartmentStored(Map<String,Object> e,String before,String after)throws Exception {
  accept(e);var item=get("/api/audit?traceId="+e.get("traceId")).path("items").get(0);
  assertEquals(before,item.path("beforeData").path("departmentCode").asText());assertEquals(after,item.path("afterData").path("departmentCode").asText());
  var persisted=json.readTree(jdbc.queryForObject("SELECT json_build_object('beforeData',before_data,'afterData',after_data)::text FROM support.audit_logs WHERE event_key=?",String.class,e.get("eventKey")));
  assertEquals(before,persisted.path("beforeData").path("departmentCode").asText());assertEquals(after,persisted.path("afterData").path("departmentCode").asText());
 }
 void assertSummaryRejected(Map<String,Object> e)throws Exception {
  var response=require(request("POST","/internal/audit/events",e,null,KEY),400);assertEquals("INVALID_AUDIT_SUMMARY",response.path("code").asText());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM support.audit_logs WHERE event_key=?",Integer.class,e.get("eventKey")));
 }
 @Test void preservesChineseDepartmentWithoutCopyingUnknownRequestText()throws Exception {
  String before="\u6570\u5b66\u5b66\u9662",after="\u8ba1\u7b97\u673a\u5b66\u9662",secret="raw-request-"+UUID.randomUUID();var e=eligibilityEvent(before,after);
  var data=new LinkedHashMap<>((Map<String,Object>)e.get("afterData"));data.put("request",Map.of("password",secret));data.put("unrelatedText",secret);e.put("afterData",data);
  assertDepartmentStored(e,before,after);String stored=jdbc.queryForObject("SELECT row_to_json(a)::text FROM support.audit_logs a WHERE event_key=?",String.class,e.get("eventKey"));assertFalse(stored.contains(secret));assertFalse(stored.contains("unrelatedText"));assertFalse(stored.contains("password"));
 }
 @Test void preservesDepartmentInternalSpaces()throws Exception {
  assertDepartmentStored(eligibilityEvent("CS FDU","SCI FDU"),"CS FDU","SCI FDU");
  // A hyphen was already accepted by the baseline; it is not a reproducer for this defect.
  assertDepartmentStored(eligibilityEvent("CS-01","SCI-02"),"CS-01","SCI-02");
 }
 @Test void acceptsDepartmentAt64CharacterLimit()throws Exception {
  String value="\u9662".repeat(64);assertDepartmentStored(eligibilityEvent(value,value),value,value);
 }
 @Test void rejectsDepartmentsOver64Characters()throws Exception {
  for(String value:List.of("A".repeat(65),"\u9662".repeat(65)))assertSummaryRejected(eligibilityEvent("CS",value));
 }
 @Test void rejectsDepartmentIsoControlCharacters()throws Exception {
  for(char control:new char[]{0,9,10,127,133})assertSummaryRejected(eligibilityEvent("CS","CS"+control+"FDU"));
 }
 @Test void keepsStatusAndReasonCodeRestricted()throws Exception {
  for(String field:List.of("status","reasonCode"))for(String value:List.of("\u5df2\u901a\u8fc7","WITH SPACE","VALID\nCODE")){
   var e=eligibilityEvent("CS","CS");var data=new LinkedHashMap<>((Map<String,Object>)e.get("afterData"));data.put(field,value);e.put("afterData",data);assertSummaryRejected(e);
  }
 }
}
