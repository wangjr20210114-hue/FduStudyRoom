package com.campus.identity.api;

import com.campus.ResourceApplication;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** IAM-05: actual identity HTTP plus an immutable workflow JAR. No foreign classes or SQL. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityWorkflowBlackBoxIntegrationTest {
    static final String RULE="identity authentication must fail closed: ";
    final JsonMapper json=JsonMapper.builder().build();
    final String password=UUID.randomUUID()+"Qa1!",serviceKey=UUID.randomUUID()+":"+UUID.randomUUID();
    final String signing=Base64.getEncoder().encodeToString(random());
    final List<HttpClient> clients=new ArrayList<>();
    final HttpClient wire=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    final AtomicInteger introspections=new AtomicInteger();
    final AtomicBoolean delayNext=new AtomicBoolean(),wrongKeyNext=new AtomicBoolean();
    volatile CountDownLatch accepted=new CountDownLatch(0),finished=new CountDownLatch(0);
    volatile long actualDelayNanos;volatile int delayedUpstreamStatus;
    ConfigurableApplicationContext identity;Process workflow;HttpServer proxy;ExecutorService executor;
    String resource,flow,room,adminRole,userRole,maintainerRole;User admin,second,technician,user1,user2;

    static byte[] random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return b;}
    static String id(){return UUID.randomUUID().toString();}
    static int port()throws IOException{try(var s=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){return s.getLocalPort();}}
    static Map<String,Object> map(Object...values){var m=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;}
    JsonNode node(HttpResponse<String> r){return json.readTree(r.body());}
    void status(int expected,HttpResponse<String> r){assertEquals(expected,r.statusCode(),RULE+"actual public HTTP status; response="+r.body());}
    String required(String name){String value=System.getenv(name);assertNotNull(value,"fixture environment missing "+name);return value;}

    boolean sandbox(){return "1".equals(System.getenv("CAMPUS_SANDBOX"));}
    String providerDatabase(String field){return required((sandbox()?"CAMPUS_FROZEN_WORKFLOW_DB_":"CAMPUS_DB_")+field);}
    Path provider()throws Exception{
        Path jar;JsonNode proof=null;
        if(sandbox()){
            jar=Path.of(required("CAMPUS_FROZEN_WORKFLOW_JAR"));Path manifest=Path.of(required("CAMPUS_FROZEN_WORKFLOW_MANIFEST"));
            assertEquals(Path.of("/opt/campus-fixtures/workflow.jar"),jar,"sandbox requires only the Stage1 read-only provider; no target fallback");
            proof=json.readTree(Files.readString(manifest));assertEquals(required("CAMPUS_FROZEN_WORKFLOW_COMMIT"),proof.get("coreCommit").asText());
            assertEquals("workflow.jar",proof.get("artifacts").get("workflow").get("file").asText());assertTrue(proof.get("trustedImage").asText().matches("sha256:[0-9a-f]{64}"));assertTrue(proof.get("productionSourceSha256").asText().matches("[0-9a-f]{64}"));
            try(var files=Files.list(jar.getParent())){assertEquals(Set.of("workflow.jar","manifest.json"),new HashSet<>(files.map(x->x.getFileName().toString()).toList()));}
        }else{
            // Trusted reactor entrypoints prepackage this exact sibling before resource-service verify.
            // No arbitrary path variable, network download or sandbox fallback is accepted here.
            jar=Path.of("..","workflow-service","target","workflow-service-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
            assertTrue(Files.isRegularFile(jar)&&!Files.isSymbolicLink(jar)&&!Files.isSymbolicLink(jar.getParent()),"ENV: trusted reactor must prepackage the fixed sibling workflow JAR");
        }
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        if(sandbox()){
            assertEquals(required("CAMPUS_FROZEN_WORKFLOW_SHA256"),digest);assertEquals(digest,proof.get("artifacts").get("workflow").get("sha256").asText());
            System.out.println("IAM05_FROZEN_WORKFLOW commit="+proof.get("coreCommit").asText()+" jarSha256="+digest+" image="+proof.get("trustedImage").asText());
        }else System.out.println("IAM05_TRUSTED_REACTOR fixedSiblingJar="+jar+" jarSha256="+digest);
        try(var z=new ZipFile(jar.toFile())){
            assertNotNull(z.getEntry("META-INF/MANIFEST.MF"),"ENV: provider must be an executable Boot JAR");
            try(var input=z.getInputStream(z.getEntry("META-INF/MANIFEST.MF"))){assertEquals("com.campus.WorkflowApplication",new java.util.jar.Manifest(input).getMainAttributes().getValue("Start-Class"),"ENV: unexpected provider Start-Class");}
            assertTrue(z.stream().anyMatch(entry->entry.getName().startsWith("BOOT-INF/classes/")),"ENV: provider production classes are missing");
        }
        return jar;
    }

    @BeforeAll void start()throws Exception{
        Path jar=provider();String db=providerDatabase("URL");assertTrue(db.startsWith("jdbc:postgresql://postgres:")||db.startsWith("jdbc:postgresql://127.0.0.1:"),"own isolated provider DB only");
        int rp=port(),wp=port();resource="http://127.0.0.1:"+rp;flow="http://127.0.0.1:"+wp;
        proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newFixedThreadPool(4);proxy.setExecutor(executor);proxy.createContext("/",this::forward);proxy.start();
        String adminName="ia"+id().replace("-","").substring(0,12);
        var boot=new SpringApplication(ResourceApplication.class);boot.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("iam05-private-fixture",map("spring.profiles.active","local","server.address","127.0.0.1","server.port",rp,"campus.auth.secure-cookies",false,"campus.auth.signing-key",signing,"campus.auth.service-key",serviceKey,"campus.bootstrap-admin.login-name",adminName,"campus.bootstrap-admin.password",password,"campus.reservation.notification-url",flow,"spring.datasource.hikari.maximum-pool-size",2))));
        identity=boot.run("--spring.profiles.active=local");health(resource,null);
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx256m","-jar",jar.toString(),"--server.address=127.0.0.1","--server.port="+wp,"--spring.profiles.active=local","--campus.auth.secure-cookies=false","--campus.workflow.identity-url=http://127.0.0.1:"+proxy.getAddress().getPort()+"/internal/auth/introspect","--campus.approval.delivery-url="+flow,"--campus.repair.workflow-url="+flow,"--spring.datasource.hikari.maximum-pool-size=2");
        var env=builder.environment();env.put("CAMPUS_AUTH_SIGNING_KEY",signing);env.put("CAMPUS_AUTH_SERVICE_KEY",serviceKey);env.put("CAMPUS_DB_URL",db);env.put("CAMPUS_DB_USER",providerDatabase("USER"));env.put("CAMPUS_DB_PASSWORD",providerDatabase("PASSWORD"));
        Path log=Files.createTempFile("iam05-workflow-",".log");workflow=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();System.out.println("IAM05_PROVIDER_PROCESS_LOG="+log);health(flow,workflow);
        admin=new User(adminName,false);var roles=admin.call(resource,"GET","/api/admin/roles",null);status(200,roles);
        for(var role:node(roles)){switch(role.get("code").asText()){case "USER"->userRole=role.get("id").asText();case "SYSTEM_ADMIN"->adminRole=role.get("id").asText();case "MAINTAINER"->maintainerRole=role.get("id").asText();default->{}}}
        assertNotNull(adminRole);assertNotNull(userRole);assertNotNull(maintainerRole);
        second=newUser();access(second,adminRole,true);second.login();technician=newUser();access(technician,maintainerRole,true);technician.login();user1=newUser();user2=newUser();
        var created=admin.call(resource,"POST","/api/resources/rooms",map("code","IAM"+id().substring(0,8),"name","IAM05 real identity room","campus","QA","building","QA","capacity",3,"openTime","00:00","closeTime","00:00","enabled",true,"bookingEnabled",true));status(201,created);room=node(created).get("id").asText();
        var config=configuration();status(200,admin.call(flow,"PUT","/api/approvals/configuration",configBody(config)));
    }

    void health(String origin,Process child)throws Exception{
        long end=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        while(System.nanoTime()<end){if(child!=null)assertTrue(child.isAlive(),"real frozen provider fixture exited; inspect private process log");try{if(wire.send(HttpRequest.newBuilder(URI.create(origin+"/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200)return;}catch(Exception ignored){}Thread.sleep(200);}fail("real fixture health timeout");
    }

    void forward(HttpExchange exchange)throws IOException{
        boolean delayed=false;long began=0;
        try{
            String path=exchange.getRequestURI().getPath();if(!path.startsWith("/internal/")){exchange.sendResponseHeaders(404,-1);return;}
            byte[] incoming=exchange.getRequestBody().readAllBytes();
            boolean introspect=path.equals("/internal/auth/introspect"),bad=false;
            if(introspect){introspections.incrementAndGet();delayed=delayNext.getAndSet(false);bad=wrongKeyNext.getAndSet(false);}
            if(delayed){began=System.nanoTime();accepted.countDown();Thread.sleep(8000);actualDelayNanos=System.nanoTime()-began;}
            var request=HttpRequest.newBuilder(URI.create(resource+exchange.getRequestURI())).timeout(Duration.ofSeconds(10));
            for(String name:List.of("Content-Type","Authorization","X-Service-Key")){String value=exchange.getRequestHeaders().getFirst(name);if(value!=null)request.header(name,bad&&name.equals("X-Service-Key")?"invalid-own-fixture-key":value);}
            var response=wire.send(request.method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(incoming)).build(),HttpResponse.BodyHandlers.ofByteArray());if(delayed)delayedUpstreamStatus=response.statusCode();
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(response.statusCode(),response.body().length);exchange.getResponseBody().write(response.body());
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception e){if(!delayed)try{exchange.sendResponseHeaders(502,-1);}catch(IOException ignored){}}
        finally{exchange.close();if(delayed)finished.countDown();}
    }

    final class User {
        final String name;String id;HttpClient http;CookieManager cookies;
        User(String name,boolean register)throws Exception{this.name=name;fresh();authenticate(register);}
        void fresh(){cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);http=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(2)).build();clients.add(http);}
        void login()throws Exception{fresh();authenticate(false);}
        void authenticate(boolean register)throws Exception{var r=call(resource,"POST",register?"/api/auth/register":"/api/auth/login",map("loginName",name,"password",password));status(register?201:200,r);id=node(r).get("id").asText();}
        String bearer(){return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("FDU_SESSION")).findFirst().orElseThrow().getValue();}
        HttpResponse<String> call(String origin,String method,String path,Object body)throws Exception{return call(origin,method,path,body,Map.of());}
        HttpResponse<String> call(String origin,String method,String path,Object body,Map<String,String> headers)throws Exception{
            var request=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/json");
            if(!method.equals("GET")){var c=http.send(HttpRequest.newBuilder(URI.create(resource+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());status(200,c);var csrf=node(c);request.header(csrf.get("headerName").asText(),csrf.get("token").asText());}
            headers.forEach(request::header);var response=http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            System.out.println("IAM05_HTTP "+method+" "+path+" STATUS="+response.statusCode()+" TRACE="+response.headers().firstValue("X-Request-Id").orElse("missing"));return response;
        }
    }
    User newUser()throws Exception{return new User("iu"+id().replace("-","").substring(0,12),true);}
    JsonNode eligibility(User user)throws Exception{var r=internal("GET","/internal/identity/users/"+user.id+"/eligibility",null,serviceKey);status(200,r);return node(r);}
    void access(User user,String role,boolean enabled)throws Exception{var before=eligibility(user);status(204,admin.call(resource,"PATCH","/api/admin/users/"+user.id+"/access",map("roleId",role,"enabled",enabled,"expectedAuthVersion",before.get("authVersion").asInt())));}
    HttpResponse<String> internal(String method,String path,String token,String key)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(resource+path)).timeout(Duration.ofSeconds(12));if(key!=null)request.header("X-Service-Key",key);if(token!=null)request.header("Authorization","Bearer "+token);
        return wire.send(request.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode configuration()throws Exception{var r=admin.call(flow,"GET","/api/approvals/configuration",null);status(200,r);return node(r);}
    Map<String,Object> configBody(JsonNode current){return map("expectedVersion",current.get("version").asInt(),"approverIds",List.of(admin.id,second.id));}
    JsonNode report(User owner)throws Exception{var r=owner.call(flow,"POST","/api/repairs",map("operationId",id(),"roomId",room,"description","IAM05 "+id()));status(201,r);return node(r);}
    JsonNode detail(User user,String repair)throws Exception{var r=user.call(flow,"GET","/api/repairs/"+repair,null);status(200,r);return node(r);}
    JsonNode history(String repair)throws Exception{var r=admin.call(flow,"GET","/api/repairs/"+repair+"/history",null);status(200,r);return node(r);}
    Map<String,Object> assignment(JsonNode order){return map("operationId",id(),"version",order.get("version").asInt(),"technicianId",technician.id);}

    @Test void workflowUsesCurrentIdentityForOwnRepairRecords()throws Exception{
        int before=introspections.get();var first=report(user1);var other=report(user2);String one=first.get("id").asText(),two=other.get("id").asText();
        assertEquals(user1.id,detail(user1,one).get("reporterId").asText(),RULE+"workflow must use actual first user");assertEquals(user2.id,detail(user2,two).get("reporterId").asText(),RULE+"workflow must use actual second user");
        status(403,user1.call(flow,"GET","/api/repairs/"+two,null));assertTrue(introspections.get()>=before+5,RULE+"workflow must actually contact identity over HTTP");
    }
    @Test void revocationReachesWorkflowWithoutTrustingOldSession()throws Exception{
        User actor=newUser();access(actor,adminRole,true);actor.login();status(200,actor.call(flow,"GET","/api/approvals/configuration",null));String old=actor.bearer();JsonNode config=configuration();
        access(actor,userRole,true);status(401,internal("POST","/internal/auth/introspect",old,serviceKey));status(401,actor.call(flow,"GET","/api/approvals/configuration",null));actor.login();
        status(403,actor.call(flow,"PUT","/api/approvals/configuration",configBody(config)));assertEquals(config,configuration(),RULE+"revoked permission must not change workflow data");
        String valid=actor.bearer();access(actor,userRole,false);status(401,internal("POST","/internal/auth/introspect",valid,serviceKey));status(401,actor.call(flow,"GET","/api/repairs",null));actor.fresh();status(401,actor.call(resource,"POST","/api/auth/login",map("loginName",actor.name,"password",password)));
    }
    @Test void forgedActorAndRoleCannotApproveOrAssign()throws Exception{
        JsonNode config=configuration();var spoof=configBody(config);spoof.put("userId",admin.id);spoof.put("role","SYSTEM_ADMIN");spoof.put("canApplyRoom",true);Map<String,String> headers=Map.of("X-User-Id",admin.id,"X-Role","SYSTEM_ADMIN");
        status(403,user1.call(flow,"PUT","/api/approvals/configuration",spoof,headers));assertEquals(config,configuration(),RULE+"self-reported administrator must not change approval configuration");
        var order=report(user1);String repair=order.get("id").asText();var before=detail(admin,repair);var events=history(repair);status(403,user1.call(flow,"POST","/api/repairs/"+repair+"/assignment",assignment(before),headers));
        assertEquals(before,detail(admin,repair),RULE+"forged actor must not assign repair");assertEquals(events,history(repair),RULE+"denied assignment creates no business history");
    }
    @Test void forgedEligibilityCannotCreateAnApproval()throws Exception{
        assertFalse(eligibility(user1).get("canApplyRoom").asBoolean());String purpose="IAM05 rejected qualification "+id();Instant start=Instant.now().plusSeconds(7200);
        var input=map("operationId",id(),"roomId",room,"startsAt",start.toString(),"endsAt",start.plusSeconds(3600).toString(),"purpose",purpose,"attendeeCount",2);
        status(403,user1.call(flow,"POST","/api/approvals/requests",input));input.put("applicantId",admin.id);input.put("canApplyRoom",true);input.put("departmentCode","CS");status(400,user1.call(flow,"POST","/api/approvals/requests",input));
        var listed=user1.call(flow,"GET","/api/approvals/requests",null);status(200,listed);var all=node(listed);JsonNode rows=all.isArray()?all:all.path("items");assertTrue(rows.isArray(),"public approval list must be observed, not treated as empty on parse failure");for(var row:rows)assertNotEquals(purpose,row.path("purpose").asText(),RULE+"self-reported qualification cannot create an approval");assertFalse(eligibility(user1).get("canApplyRoom").asBoolean());
    }
    @Test void publicIntrospectionRequiresServiceKeyAndCurrentBearer()throws Exception{
        String token=user2.bearer();var good=internal("POST","/internal/auth/introspect",token,serviceKey);status(200,good);assertEquals(user2.id,node(good).get("id").asText());assertEquals(eligibility(user2).get("authVersion").asInt(),node(good).get("authVersion").asInt());
        status(401,internal("POST","/internal/auth/introspect",token,null));status(401,internal("POST","/internal/auth/introspect",token,"invalid-own-fixture-key"));status(401,internal("POST","/internal/auth/introspect",null,serviceKey));status(401,internal("POST","/internal/auth/introspect","invalid-token",serviceKey));
        int before=introspections.get();wrongKeyNext.set(true);status(401,admin.call(flow,"GET","/api/approvals/configuration",null));assertEquals(before+1,introspections.get(),RULE+"wrong service key must be rejected by actual network provider");admin.login();
    }

    void armDelay(){accepted=new CountDownLatch(1);finished=new CountDownLatch(1);actualDelayNanos=0;delayedUpstreamStatus=0;delayNext.set(true);}
    void timeout(HttpResponse<String> response,long began)throws Exception{
        long elapsed=System.nanoTime()-began;assertTrue(accepted.await(1,TimeUnit.SECONDS),"real delayed HTTP request was accepted");status(503,response);assertEquals("IDENTITY_UNAVAILABLE",node(response).get("code").asText(),RULE+"identity timeout is diagnostic");
        assertTrue(elapsed>=Duration.ofSeconds(3).toNanos()&&elapsed<Duration.ofMillis(6500).toNanos(),RULE+"network delay must return inside fixed 3..6.5s budget; ms="+elapsed/1_000_000);
        assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(),node(response).get("traceId").asText());assertTrue(finished.await(12,TimeUnit.SECONDS),"delayed real upstream response completed");
        assertTrue(actualDelayNanos>=Duration.ofSeconds(8).toNanos(),"proxy introduced actual network delay beyond product timeout");assertEquals(200,delayedUpstreamStatus,"late provider response was real identity HTTP, not a fabricated fixture identity");System.out.println("IAM05_REAL_TIMEOUT elapsedMs="+elapsed/1_000_000+" actualProxyDelayMs="+actualDelayNanos/1_000_000+" lateActualUpstream="+delayedUpstreamStatus);
    }
    @Test void delayedIdentityCannotChangeApprovalConfiguration()throws Exception{
        admin.login();JsonNode before=configuration();armDelay();long began=System.nanoTime();var response=admin.call(flow,"PUT","/api/approvals/configuration",configBody(before));timeout(response,began);
        assertEquals(before,configuration(),RULE+"timeout and late identity success must not apply configuration mutation");status(200,admin.call(flow,"PUT","/api/approvals/configuration",configBody(before)));assertEquals(before.get("version").asInt()+1,configuration().get("version").asInt(),RULE+"only explicit retry applies once");
    }
    @Test void delayedIdentityCannotAssignRepair()throws Exception{
        admin.login();var order=report(user2);String repair=order.get("id").asText();var before=detail(admin,repair);var events=history(repair);armDelay();long began=System.nanoTime();var response=admin.call(flow,"POST","/api/repairs/"+repair+"/assignment",assignment(before));timeout(response,began);
        assertEquals(before,detail(admin,repair),RULE+"timeout and late identity success must not assign repair");assertEquals(events,history(repair),RULE+"failed identity check leaves assignment history unchanged");
    }
    @AfterAll void close()throws Exception{
        try{if(workflow!=null){workflow.destroy();if(!workflow.waitFor(10,TimeUnit.SECONDS)){workflow.destroyForcibly();workflow.waitFor(5,TimeUnit.SECONDS);}}}
        finally{try{if(identity!=null)identity.close();}finally{if(proxy!=null)proxy.stop(0);if(executor!=null){executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);}clients.forEach(HttpClient::close);wire.close();}}
    }
}
