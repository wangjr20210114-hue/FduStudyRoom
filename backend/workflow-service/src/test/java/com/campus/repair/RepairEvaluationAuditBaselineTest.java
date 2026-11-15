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
 properties={"campus.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","campus.auth.service-key=repair-isolated-fixture-service-key-000000","server.address=127.0.0.1","campus.migrations.modules=approval,repair,support","campus.repair.poll-ms=3600000"})
@Import(RepairEvaluationAuditBaselineTest.FixtureConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RepairEvaluationAuditBaselineTest {
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
 static final ExecutorService IDENTITY_EXECUTOR=Executors.newCachedThreadPool();
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
   }finally{x.close();}});IDENTITY.setExecutor(IDENTITY_EXECUTOR);IDENTITY.start();
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
 @AfterEach void closeTestClient(){http.close();}
 @AfterAll static void stopFixture()throws InterruptedException{
  IDENTITY.stop(0);
  IDENTITY_EXECUTOR.shutdown();
  if(!IDENTITY_EXECUTOR.awaitTermination(5,TimeUnit.SECONDS))IDENTITY_EXECUTOR.shutdownNow();
 }
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

 @Test void successfulEvaluationCreatesOneSanitizedAuditWithRealTrace()throws Exception{
  UUID id=order("COMPLETED",ADMIN);String sensitive="private evaluation text 私人评价";
  var response=post(ADMIN,id,"/evaluation","{\"rating\":4,\"evaluation\":\""+sensitive+"\",\"version\":1}",true);assertEquals(200,response.statusCode(),response.body());String trace=response.headers().firstValue("X-Request-Id").orElseThrow();UUID event=jdbc.queryForObject("select id from m3.repair_events where order_id=? and event_type='EVALUATE'",UUID.class,id);
  var rows=jdbc.queryForList("select id,payload::text payload from m3.repair_tasks where kind='AUDIT' and payload->>'sourceId'=?",id.toString());assertEquals(1,rows.size(),"one successful evaluation must atomically persist one unified audit task");var payload=JSON.readTree((String)rows.getFirst().get("payload"));assertEquals("M3:"+id+":EVALUATE:"+event,payload.get("eventKey").asText());assertEquals(ADMIN.toString(),payload.get("actorId").asText());assertEquals(trace,payload.get("traceId").asText());assertEquals(4,payload.get("afterData").get("score").asInt());assertEquals(2,payload.get("afterData").get("version").asInt());assertEquals("COMPLETED",payload.get("afterData").get("status").asText());assertTrue(payload.get("success").asBoolean());assertFalse(payload.toString().contains(sensitive));assertFalse(payload.toString().contains("evaluation"));
  assertEquals(409,post(ADMIN,id,"/evaluation","{\"rating\":5,\"evaluation\":\"again\",\"version\":2}",true).statusCode());assertEquals(1,jdbc.queryForObject("select count(*) from m3.repair_tasks where kind='AUDIT' and payload->>'sourceId'=?",Integer.class,id.toString()));assertEquals(1,events(id));runJobs();assertTrue(MESSAGES.stream().anyMatch(m->("M3:"+id+":EVALUATE:"+event).equals(m.get("eventKey"))&&trace.equals(m.get("traceId"))));
 }
 @Test void auditPersistenceFailureRollsBackEvaluationAndHistory()throws Exception{
  UUID id=order("COMPLETED",ADMIN);String name="audit_qa_"+id.toString().replace("-","");jdbc.execute("CREATE FUNCTION m3."+name+"() RETURNS trigger LANGUAGE plpgsql AS $$BEGIN IF NEW.kind='AUDIT' AND NEW.payload->>'sourceId'='"+id+"' THEN RAISE EXCEPTION 'controlled QA outbox failure' USING ERRCODE='23514'; END IF; RETURN NEW;END;$$");jdbc.execute("CREATE TRIGGER "+name+" BEFORE INSERT ON m3.repair_tasks FOR EACH ROW EXECUTE FUNCTION m3."+name+"()");
  try{var response=post(ADMIN,id,"/evaluation",evaluation(4,1),true);assertEquals(500,response.statusCode(),"an outbox write failure must prevent evaluation success");assertNull(row(id).get("rating"));assertNull(row(id).get("evaluated_at"));assertEquals(1,row(id).get("version"));assertEquals(0,events(id));assertEquals(0,jdbc.queryForObject("select count(*) from m3.repair_tasks where kind='AUDIT' and payload->>'sourceId'=?",Integer.class,id.toString()));}
  finally{jdbc.execute("DROP TRIGGER "+name+" ON m3.repair_tasks");jdbc.execute("DROP FUNCTION m3."+name+"()");}
 }
}
