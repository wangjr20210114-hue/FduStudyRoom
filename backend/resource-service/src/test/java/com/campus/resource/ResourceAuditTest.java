package com.campus.resource;
import com.campus.ResourceApplication;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class ResourceAuditTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String PASS="audit-resource-fixture-password";
    static class TestClock extends Clock{
        Instant now=Instant.parse("2031-01-01T00:00:00Z");
        public ZoneId getZone(){
            return ZoneOffset.UTC;
        }
        public Clock withZone(ZoneId z){
            return this;
        }
        public Instant instant(){
            return now;
        }
    }
    @TestConfiguration(proxyBeanMethods=false)static class Fixture{
        @Bean @Primary @org.springframework.beans.factory.annotation.Qualifier("resourceCheckinClock")TestClock auditClock(){
            return new TestClock();
        }
    }
    @Test void auditPersistsAndRetriesWithOneEventKey()throws Exception{
        HttpServer receiver=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        List<String> keys=new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> bodies=new java.util.concurrent.CopyOnWriteArrayList<>();
        boolean[] fail={
            true
        };
        JdbcTemplate[] sourceDb={
            null
        };
        receiver.createContext("/internal/audit/events",e->{
            String body=new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(!fail[0]&&sourceDb[0]!=null)sourceDb[0].queryForObject("SELECT 1",Integer.class);bodies.add(body);keys.add(JSON.readTree(body).path("eventKey").asText());e.sendResponseHeaders(fail[0]?503:201,-1);e.close();
        });
        receiver.start();
        ConfigurableApplicationContext app=null;
        try{
            String name="audit-"+UUID.randomUUID().toString().substring(0,8);
            app=new SpringApplicationBuilder(ResourceApplication.class,Fixture.class).run("--spring.datasource.hikari.connection-timeout=1000","--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=0","--campus.auth.secure-cookies=false","--campus.auth.signing-key="+Base64.getEncoder().encodeToString(new byte[32]),"--campus.auth.service-key=audit-resource-test-service-key-32","--campus.bootstrap-admin.login-name="+name,"--campus.bootstrap-admin.password="+PASS,"--campus.resource.audit-url=http://127.0.0.1:"+receiver.getAddress().getPort());
            String base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");
            JdbcTemplate db=app.getBean(JdbcTemplate.class);
            sourceDb[0]=db;
            assertEquals(1,db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='resource' AND table_name='resource_operations' AND column_name='audit_state'",Integer.class),"resource contract response: durable audit outbox required");
            HttpClient admin=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
            request(admin,base,"/api/auth/login",Map.of("loginName",name,"password",PASS));
            var room=request(admin,base,"/api/resources/rooms",Map.of("code","AUDIT-"+UUID.randomUUID(),"name","Audit room","campus","Handan","building","A","capacity",1,"openTime","08:00","closeTime","22:00","bookingEnabled",true,"enabled",true));
            assertEquals(201,room.statusCode());
            UUID id=UUID.fromString(JSON.readTree(room.body()).path("id").asText());
            assertEquals("PENDING",db.queryForObject("SELECT audit_state FROM resource.resource_operations WHERE kind='ROOM' AND source_id=?",String.class,id));
            var pool=app.getBean(javax.sql.DataSource.class).unwrap(com.zaxxer.hikari.HikariDataSource.class);
            pool.setMinimumIdle(0);
            pool.setMaximumPoolSize(1);
            pool.getHikariPoolMXBean().softEvictConnections();
            Object dispatcher=app.getBean(Class.forName("com.campus.resource.application.ResourceAuditDispatcher"));
            dispatcher.getClass().getMethod("dispatch").invoke(dispatcher);
            assertEquals("PENDING",db.queryForObject("SELECT audit_state FROM resource.resource_operations WHERE kind='ROOM' AND source_id=?",String.class,id));
            assertEquals(room.headers().firstValue("X-Request-Id").orElseThrow(),JSON.readTree(bodies.get(bodies.size()-1)).path("traceId").asText(),"resource contract response: audit trace must match originating HTTP request");
            fail[0]=false;
            app.getBean(TestClock.class).now=app.getBean(TestClock.class).now.plusSeconds(31);
            dispatcher.getClass().getMethod("dispatch").invoke(dispatcher);
            assertEquals("SENT",db.queryForObject("SELECT audit_state FROM resource.resource_operations WHERE kind='ROOM' AND source_id=?",String.class,id),"resource contract response: audit network must release source database transaction");
            String event="M02:"+db.queryForObject("SELECT operation_id FROM resource.resource_operations WHERE kind='ROOM' AND source_id=?",UUID.class,id);
            assertEquals(2,keys.stream().filter(event::equals).count());
            var retries=bodies.stream().filter(x->JSON.readTree(x).path("eventKey").asText().equals(event)).toList();
            assertEquals(retries.get(0),retries.get(1),"audit retry must preserve the exact saved payload");
            HttpClient user=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
            var registration=request(user,base,"/api/auth/register",Map.of("loginName","audit-user-"+UUID.randomUUID().toString().substring(0,7),"password",PASS));
            assertEquals(201,registration.statusCode());
            var denied=request(user,base,"/api/resources/rooms",Map.of("password","DO_NOT_COPY_SECRET","code","denied"));
            assertEquals(403,denied.statusCode());
            app.getBean(TestClock.class).now=app.getBean(TestClock.class).now.plusSeconds(31);
            dispatcher.getClass().getMethod("dispatch").invoke(dispatcher);
            assertTrue(bodies.stream().anyMatch(x->x.contains("RESOURCE_DENIED")));
            var failure=JSON.readTree(bodies.stream().filter(x->x.contains("RESOURCE_DENIED")).reduce((a,b)->b).orElseThrow());
            assertEquals(UUID.fromString(JSON.readTree(registration.body()).path("id").asText()).toString(),failure.path("actorId").asText(),"authenticated denial must preserve the real actor");
            assertEquals(denied.headers().firstValue("X-Request-Id").orElseThrow(),failure.path("traceId").asText(),"denial audit must preserve the originating server trace");
            int beforeDenials=db.queryForObject("SELECT count(*) FROM resource.resource_operations WHERE kind='DENIED'",Integer.class);
            var serviceDenied=admin.send(HttpRequest.newBuilder(URI.create(base+"/internal/resources/rooms")).header("X-Service-Key","invalid-service-key").GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(401,serviceDenied.statusCode());
            assertEquals(beforeDenials+1,db.queryForObject("SELECT count(*) FROM resource.resource_operations WHERE kind='DENIED'",Integer.class),"security-chain rejection is durably audited even before controller authentication");
            dispatcher.getClass().getMethod("dispatch").invoke(dispatcher);
            String denialTrace=serviceDenied.headers().firstValue("X-Request-Id").orElseThrow();
            assertTrue(bodies.stream().anyMatch(x->JSON.readTree(x).path("traceId").asText().equals(denialTrace)&&JSON.readTree(x).path("actorId").isNull()),"unauthenticated rejection keeps its server trace and an unknown actor");
            assertTrue(bodies.stream().noneMatch(x->x.contains("DO_NOT_COPY_SECRET")||x.contains(PASS)||x.contains("password")));
        }
        finally{
            if(app!=null){
                ResourceApiTest cleanup=new ResourceApiTest();
                cleanup.db=app.getBean(JdbcTemplate.class);
                cleanup.clean();
                app.close();
            }
            receiver.stop(0);
        }
    }
    static HttpResponse<String> request(HttpClient client,String base,String path,Object body)throws Exception{
        var csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        var token=JSON.readTree(csrf.body());
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json").header(token.path("headerName").asText(),token.path("token").asText()).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test void unknownRoomDenialsReachTypedAuditConsumer()throws Exception {
        var received=new java.util.concurrent.CopyOnWriteArrayList<String>();
        var accepted=new java.util.concurrent.CopyOnWriteArrayList<String>();
        HttpServer receiver=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        receiver.createContext("/internal/audit/events",e->{
            String body=new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);received.add(body);
            // Public audit contract: a present roomId must be a UUID string (AuditStore.safe).
            Map<?,?> event=JSON.readValue(body,Map.class);Map<?,?> summary=(Map<?,?>)event.get("afterData");boolean valid=true;
            if(summary.containsKey("roomId")){
                Object room=summary.get("roomId");valid=room instanceof String;
                if(valid)try{UUID.fromString((String)room);}catch(IllegalArgumentException bad){valid=false;}
            }
            int status="audit-resource-test-service-key-32".equals(e.getRequestHeaders().getFirst("X-Service-Key"))?(valid?201:400):401;
            if(status==201)accepted.add(body);e.sendResponseHeaders(status,-1);e.close();
        });receiver.start();ConfigurableApplicationContext app=null;var clients=new ArrayList<HttpClient>();
        try{
            String name="audit-null-"+UUID.randomUUID().toString().substring(0,8);
            app=new SpringApplicationBuilder(ResourceApplication.class,Fixture.class).run("--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=0","--campus.auth.secure-cookies=false","--campus.auth.signing-key="+Base64.getEncoder().encodeToString(new byte[32]),"--campus.auth.service-key=audit-resource-test-service-key-32","--campus.bootstrap-admin.login-name="+name,"--campus.bootstrap-admin.password="+PASS,"--campus.resource.audit-url=http://127.0.0.1:"+receiver.getAddress().getPort(),"--campus.resource.audit-initial-delay-millis=3600000");
            String base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");JdbcTemplate db=app.getBean(JdbcTemplate.class);
            HttpClient admin=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();clients.add(admin);
            assertEquals(200,request(admin,base,"/api/auth/login",Map.of("loginName",name,"password",PASS)).statusCode());
            var known=request(admin,base,"/api/resources/rooms",Map.of("code","NULL-AUDIT-"+UUID.randomUUID(),"name","Known room","campus","Handan","building","A","capacity",1,"openTime","08:00","closeTime","22:00","bookingEnabled",true,"enabled",true));assertEquals(201,known.statusCode());UUID roomId=UUID.fromString(JSON.readTree(known.body()).path("id").asText());
            Object dispatcher=app.getBean(Class.forName("com.campus.resource.application.ResourceAuditDispatcher"));dispatcher.getClass().getMethod("dispatch").invoke(dispatcher);
            assertEquals("SENT",db.queryForObject("SELECT audit_state FROM resource.resource_operations WHERE kind='ROOM' AND source_id=?",String.class,roomId));
            assertEquals(roomId.toString(),JSON.readTree(accepted.getLast()).path("afterData").path("roomId").asText(),"a known room must retain its UUID");
            HttpClient plain=HttpClient.newHttpClient();clients.add(plain);
            var anonymous=plain.send(HttpRequest.newBuilder(URI.create(base+"/api/resources/rooms")).header("X-Request-Id","untrusted-client-trace").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(401,anonymous.statusCode());assertNotEquals("untrusted-client-trace",anonymous.headers().firstValue("X-Request-Id").orElseThrow());
            HttpClient user=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();clients.add(user);
            var registration=request(user,base,"/api/auth/register",Map.of("loginName","null-user-"+UUID.randomUUID().toString().substring(0,7),"password",PASS));assertEquals(201,registration.statusCode());UUID actor=UUID.fromString(JSON.readTree(registration.body()).path("id").asText());
            var forbidden=request(user,base,"/api/resources/rooms",Map.of("code","denied","password","DO_NOT_COPY_NULL_AUDIT_SECRET"));assertEquals(403,forbidden.statusCode());
            var service=plain.send(HttpRequest.newBuilder(URI.create(base+"/internal/resources/rooms")).header("X-Service-Key","wrong-service-key").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(401,service.statusCode());
            var csrf=admin.send(HttpRequest.newBuilder(URI.create(base+"/api/resources/rooms")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,csrf.statusCode());assertEquals("CSRF_INVALID",JSON.readTree(csrf.body()).path("code").asText());
            dispatcher.getClass().getMethod("dispatch").invoke(dispatcher);
            assertAll("security denials must persist and reach the typed audit contract",
                ()->assertDeniedDelivered(db,received,accepted,anonymous,null,true),
                ()->assertDeniedDelivered(db,received,accepted,forbidden,actor,true),
                ()->assertDeniedDelivered(db,received,accepted,service,null,true),
                ()->assertDeniedDelivered(db,received,accepted,csrf,null,false));
            assertTrue(received.stream().noneMatch(x->x.contains(PASS)||x.contains("DO_NOT_COPY_NULL_AUDIT_SECRET")||x.contains("password")));
        }finally{
            for(HttpClient client:clients)client.close();
            if(app!=null){ResourceApiTest cleanup=new ResourceApiTest();cleanup.db=app.getBean(JdbcTemplate.class);cleanup.clean();app.close();}
            receiver.stop(0);
        }
    }
    static void assertDeniedDelivered(JdbcTemplate db,List<String> received,List<String> accepted,HttpResponse<String> response,UUID actor,boolean checkActor)throws Exception {
        String trace=response.headers().firstValue("X-Request-Id").orElseThrow();var rows=db.queryForList("SELECT operation_id,audit_state,audit_payload::text AS payload FROM resource.resource_operations WHERE kind='DENIED' AND audit_payload->>'traceId'=?",trace);assertEquals(1,rows.size(),"security failure must have one durable source event");var row=rows.getFirst();var saved=JSON.readTree((String)row.get("payload"));
        assertAll("delivery for "+trace,
            ()->assertEquals("SENT",row.get("audit_state"),"typed audit consumer must accept the denial"),
            ()->assertFalse(saved.path("afterData").has("roomId"),"unknown room must be absent, not JSON null"),
            ()->assertEquals("M02:"+row.get("operation_id"),saved.path("eventKey").asText()),
            ()->assertEquals(String.valueOf(response.statusCode()),saved.path("afterData").path("status").asText()),
            ()->assertEquals(trace,saved.path("traceId").asText()),
            ()->{if(checkActor){if(actor==null)assertTrue(saved.path("actorId").isNull());else assertEquals(actor.toString(),saved.path("actorId").asText());}},
            ()->assertEquals(1,received.stream().filter(x->JSON.readTree(x).path("traceId").asText().equals(trace)).count()),
            ()->assertEquals(1,accepted.stream().filter(x->JSON.readTree(x).path("traceId").asText().equals(trace)).count(),"typed receiver must really accept this event"));
    }
}
