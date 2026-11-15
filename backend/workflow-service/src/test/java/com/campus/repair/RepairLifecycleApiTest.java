package com.campus.repair;

import com.campus.WorkflowApplication;
import com.campus.common.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=WorkflowApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"campus.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","campus.auth.service-key=repair-isolated-fixture-service-key-000000","server.address=127.0.0.1","campus.migrations.modules=repair","campus.repair.poll-ms=3600000"})
@Import(RepairLifecycleApiTest.FixtureConfiguration.class)
class RepairLifecycleApiTest {
 static final UUID ADMIN=UUID.randomUUID(),WORKER=UUID.randomUUID(),REPORTER=UUID.randomUUID(),OTHER=UUID.randomUUID();
 static final Set<String> ALL=Set.of("repair:evaluate","repair:accept","repair:history:read","repair:report","repair:maintain","repair:assign","repair:admin-cancel");
 static final Map<UUID,Set<String>> PERMISSIONS=new ConcurrentHashMap<>();
 static final Set<UUID> REVOKED=ConcurrentHashMap.newKeySet();
 static final JsonMapper JSON=JsonMapper.builder().build();
 static final AtomicInteger LOOKUPS=new AtomicInteger();
 static final UUID ROOM=UUID.randomUUID(),SEAT=UUID.randomUUID();
 static final Map<UUID,Map<String,Object>> SOURCES=new ConcurrentHashMap<>();
 static final List<Map<String,Object>> MESSAGES=new CopyOnWriteArrayList<>();
 static final AtomicInteger RESOURCE_CALLS=new AtomicInteger();
 static volatile boolean RESOURCE_DOWN=false,LOSE_RESPONSE=false,DOWNSTREAM_DOWN=false,EMAIL_VERIFIED=true;
 static final java.util.concurrent.atomic.AtomicReference<Instant> NOW=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-16T00:00:00Z"));
 static final HttpServer IDENTITY;
 static {
  try {
   IDENTITY=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   IDENTITY.createContext("/introspect",x->{
    var settings=new AuthSettings("password","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","repair-isolated-fixture-service-key-000000",true,Duration.ofMinutes(30));
    try {
     if(!settings.serviceKey().equals(x.getRequestHeaders().getFirst("X-Service-Key"))) {x.sendResponseHeaders(403,-1);return;}
     var claims=new TokenService(settings).verify(x.getRequestHeaders().getFirst("Authorization").substring(7));
     LOOKUPS.incrementAndGet();
     if(REVOKED.contains(claims.userId())||!PERMISSIONS.containsKey(claims.userId())){x.sendResponseHeaders(401,-1);return;}
     var principal=new IdentityPrincipal(claims.userId(),"fixture","Fixture",OTHER.equals(claims.userId())?"SYSTEM_ADMIN":"USER",PERMISSIONS.get(claims.userId()),1,false);
     byte[] body=JSON.writeValueAsBytes(principal);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);
    } finally{x.close();}
   });
   IDENTITY.createContext("/",x->{try{
    if(!"repair-isolated-fixture-service-key-000000".equals(x.getRequestHeaders().getFirst("X-Service-Key"))){x.sendResponseHeaders(401,-1);return;}
    String path=x.getRequestURI().getPath();Object out;
    if(path.equals("/internal/system/settings"))out=Map.of("version",1,"repairSlaMinutes",120);
    else if(path.startsWith("/internal/identity/users")){
     if(path.endsWith("notification-email"))out=Map.of("enabled",true,"verified",EMAIL_VERIFIED,"email","fixture@example.test");
     else if(path.endsWith("eligibility")){UUID id=UUID.fromString(path.split("/")[4]);out=Map.of("userId",id,"enabled",!REVOKED.contains(id),"permissions",PERMISSIONS.getOrDefault(id,Set.of()));}
     else out=List.of(Map.of("userId",ADMIN,"displayName","Admin"),Map.of("userId",WORKER,"displayName","Worker"));
    }else if(path.startsWith("/internal/resources/rooms"))out=Map.of("id",ROOM,"name","R101","enabled",true);
    else if(path.startsWith("/internal/resources/seats"))out=Map.of("id",SEAT,"roomId",ROOM,"enabled",true);
    else if(path.startsWith("/internal/resources/restrictions/")){
     RESOURCE_CALLS.incrementAndGet();if(RESOURCE_DOWN){x.sendResponseHeaders(503,-1);return;}
     var b=JSON.readTree(x.getRequestBody().readAllBytes());UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1));int v=b.get("sourceVersion").asInt();var old=SOURCES.get(id);
     if(old!=null&&((Number)old.get("sourceVersion")).intValue()>v){x.sendResponseHeaders(409,-1);return;}
     if(old!=null&&((Number)old.get("sourceVersion")).intValue()==v)out=old;
     else{var m=new LinkedHashMap<String,Object>();m.put("sourceModule","M3");m.put("sourceId",id.toString());m.put("roomId",b.get("roomId").asText());m.put("sourceVersion",v);m.put("active",b.get("active").asBoolean());m.put("operationId",b.get("operationId").asText());m.put("effectiveAt","2026-11-16T00:00:00.123456789Z");SOURCES.put(id,m);out=m;}if(LOSE_RESPONSE){LOSE_RESPONSE=false;x.sendResponseHeaders(503,-1);return;}
    }else if(path.startsWith("/internal/resources/sources/M3/")){out=SOURCES.get(UUID.fromString(path.substring(path.lastIndexOf('/')+1)));if(out==null){x.sendResponseHeaders(404,-1);return;}}
    else{if(DOWNSTREAM_DOWN&&path.endsWith("maintenance-events")){x.sendResponseHeaders(404,-1);return;}MESSAGES.add(JSON.readValue(x.getRequestBody().readAllBytes(),Map.class));out=Map.of("status","ACCEPTED");}
    byte[] b=JSON.writeValueAsBytes(out);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);
   }finally{x.close();}});IDENTITY.setExecutor(Executors.newCachedThreadPool());IDENTITY.start();
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("campus.workflow.identity-url",()->"http://127.0.0.1:"+IDENTITY.getAddress().getPort()+"/introspect");r.add("campus.repair.workflow-url",()->"http://127.0.0.1:"+IDENTITY.getAddress().getPort());}
 @TestConfiguration(proxyBeanMethods=false) static class FixtureConfiguration {@Bean FixtureCsrf fixtureCsrf(){return new FixtureCsrf();}@Bean Clock repairClock(){return new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return NOW.get();}};}}
 @RestController static class FixtureCsrf {@GetMapping("/api/repair-fixture/csrf") Map<String,String> csrf(CsrfToken t){return Map.of("header",t.getHeaderName(),"token",t.getToken());}}
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired TokenService tokens;
 @Autowired org.springframework.context.ApplicationContext context;
 final HttpClient http=HttpClient.newHttpClient();
 @BeforeEach void reset(){RESOURCE_DOWN=false;LOSE_RESPONSE=false;DOWNSTREAM_DOWN=false;EMAIL_VERIFIED=true;NOW.set(Instant.parse("2026-11-16T00:00:00Z"));REVOKED.clear();PERMISSIONS.put(ADMIN,ALL);PERMISSIONS.put(WORKER,ALL);PERMISSIONS.put(REPORTER,Set.of("repair:report","repair:history:read"));PERMISSIONS.put(OTHER,Set.of("repair:maintain","repair:history:read"));}
 boolean table(){return jdbc.queryForObject("select to_regclass('m3.repair_orders') is not null",Boolean.class);}
 UUID order(String status,UUID accepted){UUID id=UUID.randomUUID();if(table())jdbc.update("insert into m3.repair_orders(id,reporter_id,room_id,description,technician_id,status,due_at,accepted_by,accepted_at,version) values(?,?,?,?,?,?,now()+interval '1 day',?,case when ?::uuid is null then null else now() end,1)",id,REPORTER,UUID.randomUUID(),"broken lamp",WORKER,status,accepted,accepted);return id;}
 String cookie(UUID user){return "__Host-FDU_SESSION="+tokens.issue(user,1);}
 HttpResponse<String> get(String path,UUID user)throws Exception{return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Cookie",cookie(user)).GET().build(),HttpResponse.BodyHandlers.ofString());}
 HttpResponse<String> post(UUID user,UUID id,String suffix,String body,boolean csrf)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/repairs/"+id+suffix)).header("Content-Type","application/json");
  String cookies=cookie(user);
  if(csrf){var c=get("/api/repair-fixture/csrf",user);assertEquals(200,c.statusCode(),"test CSRF fixture must be reachable");var j=JSON.readTree(c.body());b.header(j.get("header").asText(),j.get("token").asText());cookies+="; "+c.headers().firstValue("set-cookie").orElseThrow().split(";",2)[0];}
  return http.send(b.header("Cookie",cookies).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 String evaluation(int rating,int version){return "{\"rating\":"+rating+",\"evaluation\":\"repaired well 修好了\",\"version\":"+version+"}";}
 Map<String,Object> row(UUID id){return jdbc.queryForMap("select status,rating,evaluation,evaluated_at,version,accepted_by from m3.repair_orders where id=?",id);}
 int events(UUID id){return jdbc.queryForObject("select count(*) from m3.repair_events where order_id=?",Integer.class,id);}

 HttpResponse<String> call(UUID who,String path,String body)throws Exception{var c=get("/api/repair-fixture/csrf",who);assertEquals(200,c.statusCode());var t=JSON.readTree(c.body());return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Cookie",cookie(who)+"; "+c.headers().firstValue("set-cookie").orElseThrow().split(";",2)[0]).header(t.get("header").asText(),t.get("token").asText()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
 JsonNode ok(HttpResponse<String> q,int status)throws Exception{assertEquals(status,q.statusCode(),"business HTTP status: "+q.body());return JSON.readTree(q.body());}
 UUID create()throws Exception{return UUID.fromString(ok(call(REPORTER,"/api/repairs",JSON.writeValueAsString(Map.of("operationId",UUID.randomUUID(),"roomId",ROOM,"description","灯坏了 broken lamp"))),201).get("id").asText());}
 int version(UUID id){return ((Number)row(id).get("version")).intValue();}
 String command(UUID id,Map<String,Object> extra){var m=new LinkedHashMap<String,Object>();m.put("operationId",UUID.randomUUID());m.put("version",version(id));m.putAll(extra);return JSON.writeValueAsString(m);}
 void action(UUID actor,UUID id,String suffix,Map<String,Object> extra)throws Exception{ok(call(actor,"/api/repairs/"+id+suffix,command(id,extra)),200);}
 void runJobs()throws Exception{if(context.containsBean("repairFlow"))for(int i=0;i<5;i++)context.getBean("repairFlow").getClass().getMethod("dispatch").invoke(context.getBean("repairFlow"));}
 void assign(UUID id)throws Exception{action(ADMIN,id,"/assignment",Map.of("technicianId",WORKER));}
 void start(UUID id)throws Exception{action(WORKER,id,"/start",Map.of());runJobs();}
 void finish(UUID id)throws Exception{action(WORKER,id,"/finish",Map.of("report","repair done"));}
 void accept(UUID id,boolean yes)throws Exception{action(ADMIN,id,"/acceptance",Map.of("approved",yes,"comment","inspection"));runJobs();}
 void overdue()throws Exception{if(context.containsBean("repairFlow"))context.getBean("repairFlow").getClass().getMethod("scanOverdue").invoke(context.getBean("repairFlow"));}
 @Test void reportCreatesValidOwnOrder()throws Exception{int before=RESOURCE_CALLS.get();UUID id=create();assertEquals("SUBMITTED",row(id).get("status"));assertEquals(before,RESOURCE_CALLS.get());assertEquals(403,get("/api/repairs/"+id,OTHER).statusCode());assertEquals(200,get("/api/repairs/"+id,ADMIN).statusCode());assertEquals(400,call(REPORTER,"/api/repairs",JSON.writeValueAsString(Map.of("operationId",UUID.randomUUID(),"roomId",ROOM,"description"," "))).statusCode());assertEquals(400,call(REPORTER,"/api/repairs",JSON.writeValueAsString(Map.of("operationId",UUID.randomUUID(),"roomId",UUID.randomUUID(),"seatId",SEAT,"description","fault"))).statusCode());}
 @Test void reportListsOwnAndRejectsForgery()throws Exception{UUID id=create();assertTrue(ok(get("/api/repairs",REPORTER),200).toString().contains(id.toString()));assertFalse(ok(get("/api/repairs",OTHER),200).toString().contains(id.toString()));assertEquals(400,call(REPORTER,"/api/repairs",JSON.writeValueAsString(Map.of("operationId",UUID.randomUUID(),"roomId",ROOM,"description","fault","reporterId",OTHER))).statusCode());}
 @Test void assignmentPreservesDeadlineAndHistory()throws Exception{UUID id=create();assign(id);var due=jdbc.queryForObject("select due_at from m3.repair_orders where id=?",OffsetDateTime.class,id);assertEquals(NOW.get().plusSeconds(7200),due.toInstant());NOW.set(NOW.get().plusSeconds(60));action(ADMIN,id,"/assignment",Map.of("technicianId",OTHER));assertEquals(due,jdbc.queryForObject("select due_at from m3.repair_orders where id=?",OffsetDateTime.class,id));assertEquals(3,events(id));REVOKED.add(WORKER);assertEquals(400,call(ADMIN,"/api/repairs/"+id+"/assignment",command(id,Map.of("technicianId",WORKER))).statusCode());}
 @Test void assignmentConcurrencyAndAssignee()throws Exception{UUID id=create();String a=command(id,Map.of("technicianId",WORKER)),b=command(id,Map.of("technicianId",OTHER));var pool=Executors.newFixedThreadPool(2);try{var x=pool.submit(()->call(ADMIN,"/api/repairs/"+id+"/assignment",a).statusCode());var y=pool.submit(()->call(ADMIN,"/api/repairs/"+id+"/assignment",b).statusCode());var codes=new ArrayList<>(List.of(x.get(),y.get()));Collections.sort(codes);assertEquals(List.of(200,409),codes);}finally{pool.shutdownNow();}UUID who=jdbc.queryForObject("select technician_id from m3.repair_orders where id=?",UUID.class,id);assertEquals(403,call(who.equals(WORKER)?OTHER:WORKER,"/api/repairs/"+id+"/start",command(id,Map.of())).statusCode());}
 @Test void startRequiresRealAckAndExactTimestamp()throws Exception{UUID id=create();assign(id);action(WORKER,id,"/start",Map.of());assertEquals("RESTRICTION_PENDING",row(id).get("status"));runJobs();assertEquals("IN_PROGRESS",row(id).get("status"));assertTrue((Boolean)SOURCES.get(id).get("active"));assertTrue(MESSAGES.stream().anyMatch(m->id.toString().equals(String.valueOf(m.get("sourceId")))&&"2026-11-16T00:00:00.123456789Z".equals(m.get("effectiveAt"))));}
 @Test void startRetriesDurablyWithoutUnsafeAudit()throws Exception{UUID id=create();assign(id);RESOURCE_DOWN=true;action(WORKER,id,"/start",Map.of());runJobs();assertEquals("RESTRICTION_PENDING",row(id).get("status"));assertTrue(jdbc.queryForObject("select count(*) from m3.repair_tasks where order_id=? and state<>'DONE'",Integer.class,id)>0);RESOURCE_DOWN=false;LOSE_RESPONSE=false;DOWNSTREAM_DOWN=false;EMAIL_VERIFIED=true;NOW.set(NOW.get().plusSeconds(61));runJobs();assertEquals("IN_PROGRESS",row(id).get("status"));assertFalse(MESSAGES.stream().filter(m->m.containsKey("eventKey")).anyMatch(m->m.toString().contains("灯坏了")));}
 @Test void escalationBoundaryAndUniqueness()throws Exception{UUID id=create();assign(id);NOW.set(NOW.get().plusSeconds(7199));overdue();assertEquals(0,jdbc.queryForObject("select escalation_level from m3.repair_orders where id=?",Integer.class,id));NOW.set(NOW.get().plusSeconds(1));overdue();overdue();runJobs();assertEquals(1,jdbc.queryForObject("select escalation_level from m3.repair_orders where id=?",Integer.class,id));assertEquals(WORKER,jdbc.queryForObject("select technician_id from m3.repair_orders where id=?",UUID.class,id));assertEquals(1,jdbc.queryForObject("select count(*) from m3.repair_events where order_id=? and event_type='ESCALATE'",Integer.class,id));}
 @Test void escalationSkipsTerminal()throws Exception{UUID a=create();assign(a);start(a);finish(a);accept(a,true);UUID b=create();assign(b);action(REPORTER,b,"/cancel",Map.of("reason","duplicate"));NOW.set(NOW.get().plusSeconds(9000));overdue();assertEquals(0,jdbc.queryForObject("select escalation_level from m3.repair_orders where id=?",Integer.class,a));assertEquals(0,jdbc.queryForObject("select escalation_level from m3.repair_orders where id=?",Integer.class,b));}
 @Test void acceptanceReworkAndIndependentRelease()throws Exception{UUID id=create();assign(id);start(id);finish(id);assertEquals("PENDING_ACCEPTANCE",row(id).get("status"));assertEquals(403,call(WORKER,"/api/repairs/"+id+"/acceptance",command(id,Map.of("approved",true,"comment","self"))).statusCode());accept(id,false);assertEquals("IN_PROGRESS",row(id).get("status"));assertTrue((Boolean)SOURCES.get(id).get("active"));finish(id);accept(id,true);assertEquals("COMPLETED",row(id).get("status"));assertFalse((Boolean)SOURCES.get(id).get("active"));assertEquals(1,jdbc.queryForObject("select count(*) from m3.repair_events where order_id=? and event_type='REJECT'",Integer.class,id));}
 @Test void acceptanceExcludesPastWorkerAndWaitsForRestore()throws Exception{UUID id=create();assign(id);start(id);action(ADMIN,id,"/assignment",Map.of("technicianId",OTHER));action(OTHER,id,"/finish",Map.of("report","done"));assertEquals(403,call(WORKER,"/api/repairs/"+id+"/acceptance",command(id,Map.of("approved",true,"comment","past worker"))).statusCode());RESOURCE_DOWN=true;action(ADMIN,id,"/acceptance",Map.of("approved",true,"comment","safe"));runJobs();assertEquals("RESTORATION_PENDING",row(id).get("status"));RESOURCE_DOWN=false;LOSE_RESPONSE=false;DOWNSTREAM_DOWN=false;EMAIL_VERIFIED=true;NOW.set(NOW.get().plusSeconds(61));runJobs();assertEquals("COMPLETED",row(id).get("status"));}
 @Test void cancelBeforeStartIsIdempotent()throws Exception{UUID id=create();assign(id);String cmd=command(id,Map.of("reason","not needed"));int n=RESOURCE_CALLS.get();ok(call(REPORTER,"/api/repairs/"+id+"/cancel",cmd),200);int events=events(id);ok(call(REPORTER,"/api/repairs/"+id+"/cancel",cmd),200);assertEquals(events,events(id));assertEquals(n,RESOURCE_CALLS.get());assertEquals("CANCELLED",row(id).get("status"));}
 @Test void cancelRequiresReviewAndSafety()throws Exception{UUID id=create();assign(id);start(id);action(REPORTER,id,"/cancel",Map.of("reason","cancel"));assertTrue((Boolean)SOURCES.get(id).get("active"));action(ADMIN,id,"/cancel-review",Map.of("approved",true,"comment","review"));assertEquals(403,call(WORKER,"/api/repairs/"+id+"/cancel-inspection",command(id,Map.of("approved",true,"comment","self"))).statusCode());action(ADMIN,id,"/cancel-inspection",Map.of("approved",false,"comment","unsafe"));assertEquals("IN_PROGRESS",row(id).get("status"));action(REPORTER,id,"/cancel",Map.of("reason","again"));action(ADMIN,id,"/cancel-review",Map.of("approved",true,"comment","review"));action(ADMIN,id,"/cancel-inspection",Map.of("approved",true,"comment","safe"));runJobs();assertEquals("CANCELLED",row(id).get("status"));assertFalse((Boolean)SOURCES.get(id).get("active"));}

 @Test void reportDeniedCreateHasDurableSafeAudit()throws Exception{
  PERMISSIONS.put(REPORTER,Set.of("repair:history:read"));var response=call(REPORTER,"/api/repairs",JSON.writeValueAsString(Map.of("operationId",UUID.randomUUID(),"roomId",ROOM,"description","secret fault")));assertEquals(403,response.statusCode());String trace=response.headers().firstValue("X-Request-Id").orElseThrow();assertEquals(1,jdbc.queryForObject("select count(*) from m3.repair_tasks where kind='AUDIT' and order_id is null and payload->>'traceId'=?",Integer.class,trace),"denied create audit must be durable before delivery");runJobs();assertTrue(MESSAGES.stream().anyMatch(m->trace.equals(m.get("traceId"))&&Boolean.FALSE.equals(m.get("success"))));assertFalse(MESSAGES.stream().filter(m->trace.equals(m.get("traceId"))).anyMatch(m->m.toString().contains("secret fault")));assertThrows(DataAccessException.class,()->jdbc.update("insert into m3.repair_tasks(id,order_id,kind,payload,next_at) values(?,null,'NOTICE','{}',now())",UUID.randomUUID()));
 }
 @Test void assignmentDeniedAttemptHasMatchingTraceAudit()throws Exception{
  UUID id=create();PERMISSIONS.put(ADMIN,Set.of("repair:history:read"));var response=call(ADMIN,"/api/repairs/"+id+"/assignment",command(id,Map.of("technicianId",WORKER)));assertEquals(403,response.statusCode());String trace=response.headers().firstValue("X-Request-Id").orElseThrow();assertEquals(1,jdbc.queryForObject("select count(*) from m3.repair_tasks where order_id=? and kind='AUDIT' and payload->>'traceId'=?",Integer.class,id,trace),"denied assignment audit must preserve response trace");
 }
 @Test void cancelReviewReplayCannotBypassRevokedPermission()throws Exception{
  UUID id=create();assign(id);start(id);action(REPORTER,id,"/cancel",Map.of("reason","cancel"));String cmd=command(id,Map.of("approved",true,"comment","review"));ok(call(ADMIN,"/api/repairs/"+id+"/cancel-review",cmd),200);PERMISSIONS.put(ADMIN,Set.of("repair:history:read"));assertEquals(403,call(ADMIN,"/api/repairs/"+id+"/cancel-review",cmd).statusCode(),"idempotent replay must still require current action permission");PERMISSIONS.put(ADMIN,ALL);UUID second=create();String cancel=command(second,Map.of("reason","admin cancellation"));ok(call(ADMIN,"/api/repairs/"+second+"/cancel",cancel),200);PERMISSIONS.put(ADMIN,Set.of("repair:history:read"));assertEquals(403,call(ADMIN,"/api/repairs/"+second+"/cancel",cancel).statusCode(),"admin cancellation replay must require current admin permission");
 }
 @Test void supplementUnknownAckAndDownstreamRetry()throws Exception{
  UUID id=create();assign(id);LOSE_RESPONSE=true;action(WORKER,id,"/start",Map.of());runJobs();assertEquals("RESTRICTION_PENDING",row(id).get("status"));assertTrue((Boolean)SOURCES.get(id).get("active"));NOW.set(NOW.get().plusSeconds(31));DOWNSTREAM_DOWN=true;runJobs();assertEquals("IN_PROGRESS",row(id).get("status"));assertEquals(2,jdbc.queryForObject("select count(*) from m3.repair_tasks where order_id=? and kind in ('M1','M2') and state='PENDING'",Integer.class,id));DOWNSTREAM_DOWN=false;NOW.set(NOW.get().plusSeconds(31));runJobs();assertEquals(2,jdbc.queryForObject("select count(*) from m3.repair_tasks where order_id=? and kind in ('M1','M2') and state='DONE'",Integer.class,id));
 }
 @Test void supplementOtherRestrictionsAndEmptyEvaluation()throws Exception{
  UUID a=create(),b=create();assign(a);assign(b);start(a);start(b);finish(a);accept(a,true);assertFalse((Boolean)SOURCES.get(a).get("active"));assertTrue((Boolean)SOURCES.get(b).get("active"));ok(post(ADMIN,a,"/evaluation","{\"rating\":5,\"evaluation\":\"\",\"version\":"+version(a)+"}",true),200);assertEquals("",row(a).get("evaluation"));
 }
 @Test void supplementConcurrentCancelAndAcceptance()throws Exception{
  UUID id=create();assign(id);start(id);finish(id);String a=command(id,Map.of("approved",true,"comment","safe")),b=command(id,Map.of("reason","cancel"));var pool=Executors.newFixedThreadPool(2);try{var x=pool.submit(()->call(ADMIN,"/api/repairs/"+id+"/acceptance",a).statusCode());var y=pool.submit(()->call(REPORTER,"/api/repairs/"+id+"/cancel",b).statusCode());var codes=new ArrayList<>(List.of(x.get(),y.get()));Collections.sort(codes);assertEquals(List.of(200,409),codes);}finally{pool.shutdownNow();}if("CANCEL_REVIEW_PENDING".equals(row(id).get("status"))){assertTrue((Boolean)SOURCES.get(id).get("active"));action(ADMIN,id,"/cancel-review",Map.of("approved",true,"comment","review"));action(ADMIN,id,"/cancel-inspection",Map.of("approved",true,"comment","safe"));}runJobs();assertTrue(Set.of("COMPLETED","CANCELLED").contains(row(id).get("status")));assertFalse((Boolean)SOURCES.get(id).get("active"));
 }
 @Test void supplementLateStartCannotReviveSafetyCancellation()throws Exception{
  UUID id=create();assign(id);RESOURCE_DOWN=true;action(WORKER,id,"/start",Map.of());runJobs();action(REPORTER,id,"/cancel",Map.of("reason","cancel"));action(ADMIN,id,"/cancel-review",Map.of("approved",true,"comment","review"));action(ADMIN,id,"/cancel-inspection",Map.of("approved",true,"comment","safe"));RESOURCE_DOWN=false;runJobs();assertEquals("CANCELLED",row(id).get("status"));int version=((Number)SOURCES.get(id).get("sourceVersion")).intValue();NOW.set(NOW.get().plusSeconds(31));runJobs();assertEquals(version,((Number)SOURCES.get(id).get("sourceVersion")).intValue());assertFalse((Boolean)SOURCES.get(id).get("active"));assertEquals("CANCELLED",row(id).get("status"));
 }
 @Test void supplementEscalationDirectoryAndUnverifiedEmail()throws Exception{
  UUID id=create();assign(id);EMAIL_VERIFIED=false;NOW.set(NOW.get().plusSeconds(7200));overdue();runJobs();assertTrue(MESSAGES.stream().anyMatch(m->id.toString().equals(String.valueOf(m.get("sourceId")))&&ADMIN.toString().equals(String.valueOf(m.get("recipientId")))&&String.valueOf(m.get("bodyEn")).contains("deadline exceeded")&&!m.containsKey("emailAddress")),"real directory shape must yield escalation recipients with bilingual in-app delivery");
 }
}
