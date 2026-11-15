package com.campus.approval;

import com.campus.common.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
 "server.address=127.0.0.1", "campus.auth.mode=password",
 "campus.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "campus.auth.service-key=approval-isolated-fixture-service-key-32-characters",
 "campus.migrations.modules=approval,repair,support"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ApprovalHttpTest {
 static final Map<UUID,Set<String>> USERS=new ConcurrentHashMap<>();
 static final Set<UUID> REVOKED=ConcurrentHashMap.newKeySet();
 static final JsonMapper JSON=JsonMapper.builder().build();
 static final HttpServer IDENTITY=startIdentity();
 static HttpServer startIdentity() {
  try {
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/introspect",ex->{
    var settings=new AuthSettings("password","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
       "approval-isolated-fixture-service-key-32-characters",true,java.time.Duration.ofMinutes(30));
    try {
     if(!settings.serviceKey().equals(ex.getRequestHeaders().getFirst("X-Service-Key"))) {ex.sendResponseHeaders(403,-1);return;}
     var claims=new TokenService(settings).verify(ex.getRequestHeaders().getFirst("Authorization").substring(7));
     if(!USERS.containsKey(claims.userId()) || REVOKED.contains(claims.userId())) {ex.sendResponseHeaders(401,-1);return;}
     var principal=new IdentityPrincipal(claims.userId(),"fixture","Fixture","USER",USERS.get(claims.userId()),1,false);
     byte[] body=JSON.writeValueAsBytes(principal);
     ex.getResponseHeaders().set("Content-Type","application/json");
     ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);
    } catch(Exception error){ex.sendResponseHeaders(401,-1);} finally {ex.close();}
   });
   server.start();return server;
  } catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
  p.add("campus.workflow.identity-url",()->"http://127.0.0.1:"+IDENTITY.getAddress().getPort()+"/introspect");
 }
 @AfterAll static void stop(){IDENTITY.stop(0);}
 @LocalServerPort int port;
 @Autowired JdbcTemplate db;
 @Autowired TokenService tokens;
 final HttpClient http=HttpClient.newHttpClient();
 UUID applicant,first,second,other;
 @BeforeEach void users(){
  applicant=user();first=user();second=user();other=user();
 }
 UUID user(){UUID id=UUID.randomUUID();USERS.put(id,Set.of("approval:read","approval:decide"));return id;}
 boolean tables(){return db.queryForObject("select to_regclass('m2.classroom_requests') is not null and to_regclass('m2.approval_tasks') is not null",Boolean.class);}
 record Fixture(UUID id,UUID one,UUID two){}
 Fixture fixture(UUID owner,UUID a,UUID b,int levels) {
  var f=new Fixture(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
  // The RED product has no approval tables: still call the known public path and
  // observe missing business behavior, rather than failing during SQL setup.
  if(!tables())return f;
  db.update("""
   insert into m2.classroom_requests(id,applicant_id,room_id,starts_at,ends_at,purpose,attendee_count,expires_at,flow_version)
   values (?,?,?,now()+interval '1 day',now()+interval '1 day 1 hour','isolated M2-02 fixture',5,now()+interval '1 hour','fixture-v1')
   """,f.id,owner,UUID.randomUUID());
  db.update("insert into m2.approval_tasks(id,request_id,step_no,approver_id,status,due_at) values (?,?,1,?,'PENDING',now()+interval '1 hour')",f.one,f.id,a);
  if(levels>1)db.update("insert into m2.approval_tasks(id,request_id,step_no,approver_id,status) values (?,?,2,?,'WAITING')",f.two,f.id,b);
  return f;
 }
 String path(Fixture f){return "/api/approvals/requests/"+f.id;}
 HttpResponse<String> call(UUID actor,String method,String path,String body,boolean csrf)throws Exception{
  var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(15));
  String raw="01234567-89ab-cdef-0123-456789abcdef";
  String cookie=(actor==null?"":"__Host-FDU_SESSION="+tokens.issue(actor,1)+"; ");
  if(csrf) {
   byte[] bytes=raw.getBytes(StandardCharsets.UTF_8),masked=new byte[bytes.length*2];
   new java.security.SecureRandom().nextBytes(masked);
   for(int i=0;i<bytes.length;i++)masked[bytes.length+i]=(byte)(masked[i]^bytes[i]);
   cookie+="__Host-FDU_CSRF="+raw;
   req.header("X-XSRF-TOKEN",Base64.getUrlEncoder().encodeToString(masked));
  }
  if(!cookie.isEmpty())req.header("Cookie",cookie);
  if(body!=null)req.header("Content-Type","application/json");
  return http.send(req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 HttpResponse<String> decide(UUID actor,Fixture f,UUID task,int version,String decision,String comment)throws Exception{
  return call(actor,"POST",path(f)+"/tasks/"+task+"/decision",JSON.writeValueAsString(Map.of("expectedVersion",version,"decision",decision,"comment",comment)),true);
 }
 JsonNode node(HttpResponse<String> response){return JSON.readTree(response.body());}
 void status(HttpResponse<String> response,int expected){assertEquals(expected,response.statusCode(),"M2-02 HTTP business status: "+response.body());}
 @Test void orderedTwoLevelFlow()throws Exception {
  var f=fixture(applicant,first,second,2);
  var initial=call(first,"GET",path(f),null,false);status(initial,200);
  var tasks=node(initial).get("tasks");assertEquals(2,tasks.size());
  assertEquals(first.toString(),tasks.get(0).get("approverId").asString());assertEquals("PENDING",tasks.get(0).get("status").asString());
  assertEquals(second.toString(),tasks.get(1).get("approverId").asString());assertEquals("WAITING",tasks.get(1).get("status").asString());
  status(decide(second,f,f.two,1,"APPROVE",""),409);
  var one=decide(first,f,f.one,1,"APPROVE","first accepted");status(one,200);
  assertEquals(2,node(one).get("version").asInt());assertEquals("PENDING",node(one).get("status").asString());
  assertEquals("PENDING",node(one).get("tasks").get(1).get("status").asString());
  var two=decide(second,f,f.two,2,"APPROVE","second accepted");status(two,200);
  assertEquals("RESOURCE_PENDING",node(two).get("status").asString(),"all approvals must await resource confirmation");
  assertEquals(3,node(two).get("version").asInt());
  assertEquals("RESOURCE_PENDING",db.queryForObject("select status from m2.classroom_requests where id=?",String.class,f.id));
  assertEquals(2,db.queryForObject("select count(*) from m2.approval_tasks where request_id=? and acted_at is not null",Integer.class,f.id));
 }
 @Test void designatedActorAndReadScope()throws Exception{
  var f=fixture(applicant,first,second,2);
  status(decide(other,f,f.one,1,"APPROVE",""),403);
  status(call(other,"GET",path(f),null,false),403);
  status(call(applicant,"GET",path(f),null,false),200);
  status(call(second,"GET",path(f),null,false),200);
 }
 @Test void singleLevelPlanDenied()throws Exception{
  var f=fixture(applicant,first,second,1);
  var response=decide(first,f,f.one,1,"APPROVE","");status(response,422);
  assertEquals("INVALID_APPROVAL_PLAN",node(response).get("code").asString());
 }
 @Test void applicantCannotSelfApprove()throws Exception{
  var f=fixture(applicant,applicant,second,2);
  var response=decide(applicant,f,f.one,1,"APPROVE","");status(response,403);
  assertEquals("SELF_APPROVAL_FORBIDDEN",node(response).get("code").asString());
 }
 @Test void sameActorCannotApproveAnotherLevel()throws Exception{
  var f=fixture(applicant,other,first,2);
  // Legacy/imported history records the actual decision maker separately from assignment.
  if(tables()){
   db.update("update m2.approval_tasks set status='APPROVED',acted_at=now(),decided_by=? where id=?",first,f.one);
   db.update("update m2.approval_tasks set status='PENDING',due_at=now()+interval '1 hour' where id=?",f.two);
  }
  var response=decide(first,f,f.two,1,"APPROVE","");status(response,403);
  assertEquals("REPEATED_APPROVER_FORBIDDEN",node(response).get("code").asString());
 }
 @Test void concurrentDecisionAppliesOnce()throws Exception{
  var f=fixture(applicant,first,second,2);var barrier=new CyclicBarrier(2);
  try(var pool=Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->{barrier.await();return decide(first,f,f.one,1,"APPROVE","accepted");});
   var b=pool.submit(()->{barrier.await();return decide(first,f,f.one,1,"REJECT","capacity insufficient");});
   List<Integer> statuses=new ArrayList<>(List.of(a.get().statusCode(),b.get().statusCode()));Collections.sort(statuses);
   assertEquals(List.of(200,409),statuses,"same version has exactly one effective decision");
  }
  assertEquals(2,db.queryForObject("select version from m2.classroom_requests where id=?",Integer.class,f.id));
  assertEquals(1,db.queryForObject("select count(*) from m2.approval_tasks where request_id=? and acted_at is not null",Integer.class,f.id));
 }
 @Test void rejectionRequiresReason()throws Exception{
  var f=fixture(applicant,first,second,2);
  status(decide(first,f,f.one,1,"REJECT","  "),400);
  var result=decide(first,f,f.one,1,"REJECT","not suitable");status(result,200);
  assertEquals("REJECTED",node(result).get("status").asString());
  assertEquals("not suitable",node(result).get("tasks").get(0).get("comment").asString());
  assertEquals("CANCELLED",node(result).get("tasks").get(1).get("status").asString());
  status(decide(second,f,f.two,2,"APPROVE",""),409);
 }
 @Test void staleAndTerminalDecisionsDenied()throws Exception{
  var f=fixture(applicant,first,second,2);status(decide(first,f,f.one,1,"APPROVE",""),200);
  status(decide(first,f,f.one,1,"REJECT","stale"),409);
  status(decide(second,f,f.two,2,"APPROVE",""),200);
  status(decide(second,f,f.two,3,"APPROVE",""),409);
 }
 @Test void currentPermissionsAndCsrfRequired()throws Exception{
  var f=fixture(applicant,first,second,2);
  status(call(null,"GET",path(f),null,false),401);
  USERS.put(first,Set.of());
  status(decide(first,f,f.one,1,"APPROVE",""),403);
  USERS.put(first,Set.of("approval:read","approval:decide"));
  status(call(first,"POST",path(f)+"/tasks/"+f.one+"/decision","{\"expectedVersion\":1,\"decision\":\"APPROVE\",\"comment\":\"\"}",false),403);
  REVOKED.add(first);status(decide(first,f,f.one,1,"APPROVE",""),401);
 }
 @Test void expiryAndMalformedBodyDoNotChangeState()throws Exception{
  var f=fixture(applicant,first,second,2);
  status(call(first,"POST",path(f)+"/tasks/"+f.one+"/decision","{\"expectedVersion\":1,\"decision\":\"APPROVE\",\"role\":\"SYSTEM_ADMIN\"}",true),400);
  if(tables()) db.update("update m2.classroom_requests set expires_at=now()-interval '1 second' where id=?",f.id);
  status(decide(first,f,f.one,1,"APPROVE",""),409);
 }
 @Test void postgresConstraintsAndHistoryAreReal(){
  assertTrue(tables(),"approval persistence must use real m2 tables");
  assertEquals("PostgreSQL",db.execute((java.sql.Connection c)->c.getMetaData().getDatabaseProductName()));
  var f=fixture(applicant,first,second,2);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("insert into m2.approval_tasks(request_id,step_no,approver_id,status) values (?,3,?,'WAITING')",f.id,first));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("update m2.approval_tasks set status='REJECTED',acted_at=now(),comment='  ' where id=?",f.one));
  assertEquals(1,db.queryForObject("select count(*) from approval_meta.flyway_schema_history where success and version='2026111501'",Integer.class));
 }
}
