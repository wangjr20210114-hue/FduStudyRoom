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
 properties={"campus.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","campus.auth.service-key=repair-isolated-fixture-service-key-000000","server.address=127.0.0.1","campus.migrations.modules=repair"})
@Import(RepairEvaluationApiTest.FixtureConfiguration.class)
class RepairEvaluationApiTest {
 static final UUID ADMIN=UUID.randomUUID(),WORKER=UUID.randomUUID(),REPORTER=UUID.randomUUID(),OTHER=UUID.randomUUID();
 static final Set<String> ALL=Set.of("repair:evaluate","repair:accept","repair:history:read");
 static final Map<UUID,Set<String>> PERMISSIONS=new ConcurrentHashMap<>();
 static final Set<UUID> REVOKED=ConcurrentHashMap.newKeySet();
 static final JsonMapper JSON=JsonMapper.builder().build();
 static final AtomicInteger LOOKUPS=new AtomicInteger();
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
   });IDENTITY.start();
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("campus.workflow.identity-url",()->"http://127.0.0.1:"+IDENTITY.getAddress().getPort()+"/introspect");}
 @TestConfiguration(proxyBeanMethods=false) static class FixtureConfiguration {@Bean FixtureCsrf fixtureCsrf(){return new FixtureCsrf();}}
 @RestController static class FixtureCsrf {@GetMapping("/api/repair-fixture/csrf") Map<String,String> csrf(CsrfToken t){return Map.of("header",t.getHeaderName(),"token",t.getToken());}}
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired TokenService tokens;
 final HttpClient http=HttpClient.newHttpClient();
 @BeforeEach void reset(){REVOKED.clear();PERMISSIONS.put(ADMIN,ALL);PERMISSIONS.put(WORKER,ALL);PERMISSIONS.put(REPORTER,ALL);PERMISSIONS.put(OTHER,ALL);}
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
 @Test void ratingsPersistForActualAcceptor()throws Exception{
  for(int rating:List.of(1,5)){var id=order("COMPLETED",ADMIN);var r=post(ADMIN,id,"/evaluation",evaluation(rating,1),true);assertEquals(200,r.statusCode(),"authorized accepted_by must persist legal rating: "+r.body());var row=row(id);assertEquals(rating,((Number)row.get("rating")).intValue());assertEquals("repaired well 修好了",row.get("evaluation"));assertNotNull(row.get("evaluated_at"));assertEquals(2,row.get("version"));assertEquals(1,events(id));assertEquals("EVALUATE",jdbc.queryForObject("select event_type from m3.repair_events where order_id=?",String.class,id));}
 }
 @Test void invalidScoresAndTextNeverMutate()throws Exception{
  for(String score:List.of("0","6","1.5","null","\"5\"","true")){var id=order("COMPLETED",ADMIN);var r=post(ADMIN,id,"/evaluation","{\"rating\":"+score+",\"evaluation\":\"test\",\"version\":1}",true);assertEquals(400,r.statusCode(),"invalid rating must be rejected: "+score);assertNull(row(id).get("rating"));assertEquals(1,row(id).get("version"));assertEquals(0,events(id));}
  for(String text:List.of("\"   \"","null","123")){var id=order("COMPLETED",ADMIN);assertEquals(400,post(ADMIN,id,"/evaluation","{\"rating\":3,\"evaluation\":"+text+",\"version\":1}",true).statusCode());assertNull(row(id).get("rating"));}
 }
 @Test void currentPermissionsAndAcceptorBinding()throws Exception{
  var id=order("COMPLETED",ADMIN);
  assertEquals(403,post(OTHER,id,"/evaluation",evaluation(3,1),true).statusCode(),"SYSTEM_ADMIN role text cannot substitute accepted_by");
  assertEquals(403,post(REPORTER,id,"/evaluation",evaluation(3,1),true).statusCode(),"reporter cannot substitute actual acceptor");
  PERMISSIONS.put(ADMIN,Set.of("repair:history:read"));assertEquals(403,post(ADMIN,id,"/evaluation",evaluation(3,1),true).statusCode(),"current revoked permission must take effect");
  PERMISSIONS.put(ADMIN,ALL);REVOKED.add(ADMIN);assertEquals(401,get("/api/repairs/"+id+"/history",ADMIN).statusCode());assertNull(row(id).get("rating"));assertEquals(1,row(id).get("version"));
 }
 @Test void optimisticConcurrencyPreventsDuplicateEvaluation()throws Exception{
  var id=order("COMPLETED",ADMIN);var pool=Executors.newFixedThreadPool(2);
  try {var a=pool.submit(()->post(ADMIN,id,"/evaluation",evaluation(2,1),true).statusCode());var b=pool.submit(()->post(ADMIN,id,"/evaluation",evaluation(5,1),true).statusCode());var codes=new ArrayList<>(List.of(a.get(),b.get()));Collections.sort(codes);assertEquals(List.of(200,409),codes,"one version accepts exactly one evaluation");assertEquals(2,row(id).get("version"));assertEquals(1,events(id));assertEquals(409,post(ADMIN,id,"/evaluation",evaluation(4,2),true).statusCode());assertEquals(1,events(id));}finally{pool.shutdownNow();}
 }
 @Test void cookieCsrfAndStrictPayload()throws Exception{
  var id=order("COMPLETED",ADMIN);assertEquals(403,post(ADMIN,id,"/evaluation",evaluation(3,1),false).statusCode());
  assertEquals(400,post(ADMIN,id,"/evaluation","{\"rating\":3,\"evaluation\":\"ok\",\"version\":1,\"actorId\":\""+ADMIN+"\"}",true).statusCode(),"request identity fields must not be trusted");
  var pending=order("PENDING_ACCEPTANCE",null);assertEquals(409,post(ADMIN,pending,"/evaluation",evaluation(3,1),true).statusCode());assertNull(row(id).get("rating"));
  var uri=URI.create("http://127.0.0.1:"+port+"/api/repairs/"+id+"/history");assertEquals(401,http.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
 }
 @Test void historyRetainsEveryRepairAndAcceptance()throws Exception{
  var id=order("COMPLETED",ADMIN);List<String> types=List.of("FINISH","REJECT","FINISH","ACCEPT");List<String> from=List.of("IN_PROGRESS","PENDING_ACCEPTANCE","IN_PROGRESS","PENDING_ACCEPTANCE");List<String> to=List.of("PENDING_ACCEPTANCE","IN_PROGRESS","PENDING_ACCEPTANCE","COMPLETED");
  if(table())for(int i=0;i<4;i++)jdbc.update("insert into m3.repair_events(id,order_id,actor_id,event_type,from_status,to_status,note,created_at,updated_at,order_version) values(?,?,?,?,?,?,?,?::timestamptz,?::timestamptz,?)",UUID.randomUUID(),id,i%2==0?WORKER:ADMIN,types.get(i),from.get(i),to.get(i),"round-"+i,"2026-11-15T0"+i+":00:00Z","2026-11-15T0"+i+":00:00Z",i+1);
  var r=get("/api/repairs/"+id+"/history",REPORTER);assertEquals(200,r.statusCode(),"read persisted multi-round history");var list=JSON.readTree(r.body());assertEquals(4,list.size());
  for(int i=0;i<4;i++){var e=list.get(i);assertEquals(types.get(i),e.get("eventType").asText());assertEquals(from.get(i),e.get("fromStatus").asText());assertEquals(to.get(i),e.get("toStatus").asText());assertEquals("round-"+i,e.get("note").asText());assertEquals((i%2==0?WORKER:ADMIN).toString(),e.get("actorId").asText());assertEquals(Instant.parse("2026-11-15T0"+i+":00:00Z"),Instant.parse(e.get("createdAt").asText()));}
  assertEquals(200,post(ADMIN,id,"/evaluation",evaluation(5,1),true).statusCode());assertEquals(5,JSON.readTree(get("/api/repairs/"+id+"/history",REPORTER).body()).size());
  assertThrows(DataAccessException.class,()->jdbc.update("update m3.repair_events set note='overwritten' where order_id=?",id));assertThrows(DataAccessException.class,()->jdbc.update("delete from m3.repair_events where order_id=?",id));
  assertEquals(403,get("/api/repairs/"+id+"/history",OTHER).statusCode(),"unrelated user cannot read another order");PERMISSIONS.put(REPORTER,Set.of());assertEquals(403,get("/api/repairs/"+id+"/history",REPORTER).statusCode());
 }
 @Test void technicianCannotImpersonateAcceptor()throws Exception{
  for(String suffix:List.of("/acceptance","/evaluation")){var id=order("PENDING_ACCEPTANCE",null);String forged="{\"rating\":5,\"evaluation\":\"ok\",\"version\":1,\"acceptedBy\":\""+ADMIN+"\",\"approved\":true,\"comment\":\"ok\"}";assertEquals(403,post(WORKER,id,suffix,forged,true).statusCode(),"actual technician must be rejected even with all permissions and forged identity");assertEquals("PENDING_ACCEPTANCE",row(id).get("status"));assertNull(row(id).get("rating"));assertEquals(1,row(id).get("version"));assertEquals(0,events(id));}
 }
 @Test void authorizedAcceptanceRemainsExplicitlyPending()throws Exception{
  var id=order("PENDING_ACCEPTANCE",null);var r=post(ADMIN,id,"/acceptance","{\"approved\":true,\"comment\":\"checked\",\"version\":1}",true);assertEquals(409,r.statusCode());assertEquals("ACCEPTANCE_INTEGRATION_PENDING",JSON.readTree(r.body()).get("code").asText());assertEquals("PENDING_ACCEPTANCE",row(id).get("status"));assertEquals(0,events(id));
 }
 @Test void databaseConstraintsAndReplay()throws Exception{
  var id=order("COMPLETED",ADMIN);assertEquals(200,post(ADMIN,id,"/evaluation",evaluation(1,1),true).statusCode());assertThrows(DataAccessException.class,()->jdbc.update("update m3.repair_orders set rating=0 where id=?",id));assertThrows(DataAccessException.class,()->jdbc.update("update m3.repair_orders set accepted_by=technician_id where id=?",id));
  assertThrows(DataAccessException.class,()->jdbc.update("update m3.repair_orders set rating=4 where id=?",id));
  var flyway=org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).defaultSchema("repair_meta").schemas("repair_meta","m3").locations("classpath:db/modules/repair/migration").cleanDisabled(true).load();assertEquals(0,flyway.migrate().migrationsExecuted);assertEquals(1,((Number)row(id).get("rating")).intValue());
 }
}
