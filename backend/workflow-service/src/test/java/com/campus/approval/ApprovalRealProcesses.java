package com.campus.approval;
import java.io.*;import java.net.*;import java.net.http.*;import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.function.*;
import com.sun.net.httpserver.*;import tools.jackson.databind.*;import tools.jackson.databind.json.JsonMapper;import static org.junit.jupiter.api.Assertions.*;
/** Actual two independent JVMs. Proxy only injects a resource outage; no successful business reply is fabricated. */
final class ApprovalRealProcesses implements AutoCloseable {
 final JsonMapper json=JsonMapper.builder().build();final String key=UUID.randomUUID()+":"+UUID.randomUUID(),signing=Base64.getEncoder().encodeToString(random()),pass=UUID.randomUUID()+"Aa1!";
 final int rp=port(),wp=port();final String resource="http://127.0.0.1:"+rp,workflow="http://127.0.0.1:"+wp;
 final HttpClient wire=HttpClient.newHttpClient();Process resourceProcess,workflowProcess;HttpServer proxy;volatile boolean block;Path jar;
 static byte[] random(){byte[] x=new byte[32];new SecureRandom().nextBytes(x);return x;}
 static int port(){try(var s=new ServerSocket(0,0,InetAddress.getLoopbackAddress())){return s.getLocalPort();}catch(Exception e){throw new IllegalStateException(e);}}
 Map<String,Object> m(Object...x){return ApprovalLifecycleTest.map(x);}JsonNode node(HttpResponse<String>r){return json.readTree(r.body());}
 String root(){return System.getProperty("java.home")+"/bin/java";}
 Map<String,String> env(String db){var e=new HashMap<>(System.getenv());e.put("CAMPUS_DB_URL",db);e.put("CAMPUS_AUTH_SIGNING_KEY",signing);e.put("CAMPUS_AUTH_SERVICE_KEY",key);return e;}
 Process launch(List<String>cmd,Map<String,String>environment,String label)throws Exception{var b=new ProcessBuilder(cmd);b.environment().putAll(environment);Path log=Files.createTempFile("approval-"+label,".log");return b.redirectErrorStream(true).redirectOutput(log.toFile()).start();}
 void health(String url,Process process)throws Exception{long end=System.nanoTime()+Duration.ofSeconds(90).toNanos();while(System.nanoTime()<end){assertTrue(process.isAlive(),"fixture process must stay alive");try{if(wire.send(HttpRequest.newBuilder(URI.create(url+"/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200)return;}catch(Exception ignored){}Thread.sleep(200);}fail("fixture health timed out");}
 void startResource(String username)throws Exception{var e=env(System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_DB_URL",System.getenv("CAMPUS_DB_URL")));e.put("CAMPUS_BOOTSTRAP_ADMIN_LOGIN",username);e.put("CAMPUS_BOOTSTRAP_ADMIN_PASSWORD",pass);
 resourceProcess=launch(List.of(root(),"-Xmx320m","-jar",jar.toAbsolutePath().toString(),"--server.address=127.0.0.1","--server.port="+rp,"--spring.profiles.active=local","--campus.auth.secure-cookies=false"),e,"resource");health(resource,resourceProcess);}
 void startWorkflow()throws Exception{String cp=String.join(File.pathSeparator,Arrays.stream(System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")).split(java.util.regex.Pattern.quote(File.pathSeparator))).filter(x->!x.endsWith("test-classes")).toList());var e=env(ApprovalLifecycleTest.TEST_DB);
 workflowProcess=launch(List.of(root(),"-Xmx256m","-cp",cp,"com.campus.WorkflowApplication","--server.address=127.0.0.1","--server.port="+wp,"--spring.profiles.active=local","--campus.auth.secure-cookies=false","--campus.migrations.modules=approval,support","--campus.workflow.identity-url=http://127.0.0.1:"+proxy.getAddress().getPort()+"/internal/auth/introspect","--campus.approval.delivery-url="+workflow,"--spring.datasource.hikari.maximum-pool-size=2"),e,"workflow");health(workflow,workflowProcess);}
 void stop(Process p)throws Exception{if(p!=null){p.destroy();if(!p.waitFor(10,TimeUnit.SECONDS)){p.destroyForcibly();assertTrue(p.waitFor(5,TimeUnit.SECONDS));}}}
 class Client {
  final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();final String login;UUID id;
  Client(String login){this.login=login;}
  HttpResponse<String> call(String base,String method,String path,Object body)throws Exception{
   var q=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10));
   if(!method.equals("GET")){var csrf=http.send(HttpRequest.newBuilder(URI.create(resource+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,csrf.statusCode());var c=node(csrf);q.header(c.get("headerName").asString(),c.get("token").asString());}
   if(body!=null)q.header("Content-Type","application/json");return http.send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  }
  void login(boolean register)throws Exception{var r=call(resource,"POST",register?"/api/auth/register":"/api/auth/login",m("loginName",login,"password",pass));for(int attempt=0;!register&&r.statusCode()==401&&attempt<6;attempt++){Thread.sleep(350);r=call(resource,"POST","/api/auth/login",m("loginName",login,"password",pass));}assertEquals(register?201:200,r.statusCode(),"real IAM login");id=UUID.fromString(node(r).get("id").asString());}
 }
 HttpResponse<String> internal(String method,String path,Object body)throws Exception{var q=HttpRequest.newBuilder(URI.create(resource+path)).timeout(Duration.ofSeconds(10)).header("X-Service-Key",key);if(body!=null)q.header("Content-Type","application/json");return wire.send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());}
 void await(Callable<Boolean> condition,String description)throws Exception{long end=System.nanoTime()+Duration.ofSeconds(60).toNanos();while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(200);}fail(description);}
 void verify()throws Exception{
  jar=Path.of(System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_JAR","../resource-service/target/resource-service-0.1.0-SNAPSHOT.jar"));assertTrue(Files.isRegularFile(jar),"real resource JAR fixture is required; run full reactor or provide approved fixture");
  String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));String expected=System.getenv("CAMPUS_FROZEN_RESOURCE_SHA256");if(expected!=null)assertEquals(expected,sha,"frozen resource SHA");String manifest=System.getenv("CAMPUS_FROZEN_RESOURCE_MANIFEST");if(manifest!=null){var proof=json.readTree(Files.readString(Path.of(manifest)));assertEquals(sha,proof.get("artifacts").get("resource").get("sha256").asString());assertEquals(System.getenv("CAMPUS_FROZEN_RESOURCE_COMMIT"),proof.get("coreCommit").asString());}
  System.out.println("M04 REAL_RESOURCE_JAR_SHA256="+sha+" COMMIT="+System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_COMMIT","current-reactor"));
  String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10),adminName="da"+suffix;
  startResource(adminName);var admin=new Client(adminName);admin.login(false);var owner=new Client("do"+suffix);owner.login(true);var second=new Client("db"+suffix);second.login(true);
  var settings=admin.call(resource,"GET","/api/admin/settings",null);assertEquals(200,settings.statusCode());assertEquals(200,admin.call(resource,"PUT","/api/admin/settings",m("expectedVersion",node(settings).get("version").asInt(),"maxReservationHours",4,"beforeStartReminderMinutes",15,"afterStartReminderMinutes",10,"checkinDeadlineMinutes",15,"approvalTtlMinutes",60,"repairSlaMinutes",120)).statusCode());
  var roles=admin.call(resource,"GET","/api/admin/roles",null);assertEquals(200,roles.statusCode());String role=null;for(var r:node(roles))if(r.get("code").asString().equals("SYSTEM_ADMIN"))role=r.get("id").asString();assertNotNull(role);
  assertEquals(204,admin.call(resource,"PATCH","/api/admin/users/"+second.id+"/access",m("roleId",role,"enabled",true,"expectedAuthVersion",1)).statusCode());second.login(false);
  assertEquals(204,admin.call(resource,"PATCH","/api/admin/users/"+owner.id+"/eligibility",m("departmentCode","CS","canApplyRoom",true,"expectedAuthVersion",1)).statusCode());owner.login(false);
  var rr=admin.call(resource,"POST","/api/resources/rooms",m("code","D"+suffix,"name","Integration room","campus","Test","building","T","capacity",2,"openTime","00:00","closeTime","00:00","enabled",true,"bookingEnabled",true));assertEquals(201,rr.statusCode());String room=node(rr).get("id").asString();
  for(String code:List.of("A01","A02"))assertEquals(201,admin.call(resource,"POST","/api/resources/rooms/"+room+"/seats",m("code",code,"socketType","NONE","enabled",true)).statusCode());
  proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);proxy.setExecutor(Executors.newCachedThreadPool());proxy.createContext("/",ex->{try{
   if(block&&ex.getRequestURI().getPath().startsWith("/internal/resources/")){byte[] bytes="{\"code\":\"INJECTED_RESOURCE_OUTAGE\"}".getBytes();ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(503,bytes.length);ex.getResponseBody().write(bytes);return;}
   var q=HttpRequest.newBuilder(URI.create(resource+ex.getRequestURI())).timeout(Duration.ofSeconds(8));for(String h:List.of("X-Service-Key","Authorization","Content-Type","X-Request-Id")){String v=ex.getRequestHeaders().getFirst(h);if(v!=null)q.header(h,v);}
   var r=wire.send(q.method(ex.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(ex.getRequestBody().readAllBytes())).build(),HttpResponse.BodyHandlers.ofByteArray());ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(r.statusCode(),r.body().length);ex.getResponseBody().write(r.body());
  }catch(Exception err){try{ex.sendResponseHeaders(503,-1);}catch(Exception ignored){}}finally{ex.close();}});proxy.start();
  startWorkflow();var cfg=admin.call(workflow,"GET","/api/approvals/configuration",null);assertEquals(200,cfg.statusCode());assertEquals(200,admin.call(workflow,"PUT","/api/approvals/configuration",m("expectedVersion",node(cfg).get("version").asInt(),"approverIds",List.of(admin.id,second.id))).statusCode());
  Instant from=Instant.now().plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),to=from.plusSeconds(3600);
  var sub=owner.call(workflow,"POST","/api/approvals/requests",m("operationId",UUID.randomUUID(),"roomId",room,"startsAt",from.toString(),"endsAt",to.toString(),"purpose","Real network validation","attendeeCount",2));assertEquals(201,sub.statusCode());String request=node(sub).get("requestId").asString(),path="/api/approvals/requests/"+request;
  block=true;
  for(int i=0;i<2;i++){var view=owner.call(workflow,"GET",path,null);var v=node(view);assertEquals(200,view.statusCode());assertEquals(200,(i==0?admin:second).call(workflow,"POST",path+"/tasks/"+v.get("tasks").get(i).get("id").asString()+"/decision",m("expectedVersion",v.get("version").asInt(),"decision","APPROVE")).statusCode());}
  await(()->{var v=node(owner.call(workflow,"GET",path,null));assertNotEquals("APPROVED",v.get("status").asString());for(var j:v.get("deliveries"))if(j.get("kind").asString().equals("ALLOCATE")&&j.get("attempts").asInt()>0)return true;return false;},"persisted failed allocation must be observable");
  stop(workflowProcess);workflowProcess=null;stop(resourceProcess);resourceProcess=null;startResource(adminName);block=false;startWorkflow();
  await(()->node(owner.call(workflow,"GET",path,null)).get("status").asString().equals("APPROVED"),"restarted workflow must resume persisted original command");
  var source=internal("GET","/internal/resources/sources/M2/"+request,null);assertEquals(200,source.statusCode());assertTrue(node(source).get("active").asBoolean());assertEquals(2,node(source).get("allocationCount").asInt());
  var seats=internal("GET","/internal/resources/availability?startsAt="+from+"&endsAt="+to+"&userId="+owner.id,null);assertEquals(200,seats.statusCode());assertFalse(node(seats).toString().contains(room),"confirmed M2 must remove all room seats");
  var v=node(owner.call(workflow,"GET",path,null));assertEquals(200,owner.call(workflow,"POST",path+"/cancel",m("operationId",UUID.randomUUID(),"expectedVersion",v.get("version").asInt(),"reason","")).statusCode());
  await(()->!node(internal("GET","/internal/resources/sources/M2/"+request,null)).get("active").asBoolean(),"cancel must really release source");
  var available=internal("GET","/internal/resources/availability?startsAt="+from+"&endsAt="+to+"&userId="+owner.id,null);assertEquals(200,available.statusCode());int count=0;for(var seat:node(available))if(seat.get("roomId").asString().equals(room))count++;assertEquals(2,count);
  System.out.println("M04 TWO_JVM_HTTP_RECOVERY PASS request="+request+" source=M2 wholeRoom=2 released=2");
 }
 public void close()throws Exception{try{stop(workflowProcess);}finally{try{stop(resourceProcess);}finally{if(proxy!=null){proxy.stop(0);((ExecutorService)proxy.getExecutor()).shutdownNow();}}}}
}
