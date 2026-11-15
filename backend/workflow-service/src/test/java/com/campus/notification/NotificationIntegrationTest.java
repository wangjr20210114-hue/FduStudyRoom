package com.campus.notification;

import com.campus.WorkflowApplication;
import com.campus.common.security.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationIntegrationTest {
    static final String KEY="notification-integration-service-key-000000000000";
    static final String SIGN=Base64.getEncoder().encodeToString(new byte[32]);
    final UUID user1=UUID.randomUUID(),user2=UUID.randomUUID(),denied=UUID.randomUUID();
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    final AtomicBoolean permit=new AtomicBoolean(true);
    final String smtpHost=env("CAMPUS_TEST_SMTP_HOST",System.getenv("CAMPUS_SANDBOX")!=null?"smtp":"127.0.0.1");
    final int smtpPort=Integer.parseInt(env("CAMPUS_TEST_SMTP_PORT","1025"));
    final String inbox=env("CAMPUS_TEST_MAILPIT_URL","http://"+smtpHost+":8025");
    ConfigurableApplicationContext context; HttpServer identity; String base; JdbcTemplate jdbc; TokenService tokens;
    String env(String key,String fallback){var value=System.getenv(key);return value==null||value.isBlank()?fallback:value;}
    @BeforeAll void start()throws Exception {
        tokens=new TokenService(new AuthSettings("password",SIGN,KEY,true,Duration.ofMinutes(30)));
        identity=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        identity.createContext("/introspect",exchange->{
            if(!KEY.equals(exchange.getRequestHeaders().getFirst("X-Service-Key"))){exchange.sendResponseHeaders(401,-1);exchange.close();return;}
            var claims=tokens.verify(exchange.getRequestHeaders().getFirst("Authorization").substring(7));
            var permissions=permit.get()&&!claims.userId().equals(denied)?Set.of("notification:read"):Set.<String>of();
            var principal=new IdentityPrincipal(claims.userId(),"fixture","Fixture","USER",permissions,1,false);
            byte[] body=json.writeValueAsBytes(principal);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });identity.start();
        String db=env("CAMPUS_DB_URL",env("CAMPUS_TEST_DB_URL","jdbc:postgresql://127.0.0.1:5432/campus_migration_probe"));
        String dbUser=env("CAMPUS_DB_USER",env("CAMPUS_TEST_DB_USER","campus"));
        String dbPassword=env("CAMPUS_DB_PASSWORD",env("CAMPUS_TEST_DB_PASSWORD",""));
        context=new SpringApplicationBuilder(WorkflowApplication.class).run("--server.address=127.0.0.1","--server.port=0","--spring.datasource.url="+db,"--spring.datasource.username="+dbUser,"--spring.datasource.password="+dbPassword,"--campus.auth.mode=password","--campus.auth.signing-key="+SIGN,"--campus.auth.service-key="+KEY,"--campus.auth.secure-cookies=true","--campus.workflow.identity-url=http://127.0.0.1:"+identity.getAddress().getPort()+"/introspect","--campus.notifications.initial-delay-ms=3600000","--campus.notifications.smtp.host="+smtpHost,"--campus.notifications.smtp.port="+smtpPort,"--campus.notifications.smtp.from=studyroom@example.test","--campus.notifications.smtp.allow-insecure=true");
        base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");jdbc=context.getBean(JdbcTemplate.class);
    }
    @AfterAll void stop(){if(context!=null)context.close();if(identity!=null)identity.stop(0);}
    @BeforeEach void restore(){permit.set(true);if(context!=null)context.getEnvironment().getPropertySources().remove("notification-test-smtp");}
    Map<String,Object> event(UUID recipient,Instant at,String email){
        var m=new LinkedHashMap<String,Object>();m.put("eventId",UUID.randomUUID());m.put("recipientId",recipient);m.put("sourceModule","M1");m.put("sourceId",UUID.randomUUID());m.put("title","自习提醒");m.put("body","H3101 09:00-10:00 自习提醒");m.put("titleEn","Study reminder");m.put("bodyEn","H3101 09:00-10:00 study reminder");m.put("scheduledAt",at.toString());if(email!=null)m.put("emailAddress",email);return m;
    }
    HttpResponse<String> request(String method,String path,Object body,String cookie,String key,String csrf)throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10));
        if(cookie!=null)b.header("Cookie",cookie);if(key!=null)b.header("X-Service-Key",key);if(csrf!=null)b.header("X-XSRF-TOKEN",csrf);
        if(body!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    String cookie(UUID id){return "__Host-FDU_SESSION="+tokens.issue(id,1);}
    JsonNode accepted(Map<String,Object> input)throws Exception {var r=request("POST","/internal/notifications/events",input,null,KEY,null);assertEquals(201,r.statusCode(),"notification event must be durably accepted via service HTTP: "+r.body());return json.readTree(r.body());}
    JsonNode list(UUID id)throws Exception {var r=request("GET","/api/notifications?size=100",null,cookie(id),null,null);assertEquals(200,r.statusCode(),r.body());return json.readTree(r.body());}
    JsonNode item(UUID id,String notification)throws Exception {for(var v:list(id).path("items"))if(v.path("id").asText().equals(notification))return v;fail("own notification missing "+notification);return null;}
    void dispatch()throws Exception {context.getBean("notificationDelivery").getClass().getMethod("deliverDue").invoke(context.getBean("notificationDelivery"));}
    String[] csrf(UUID id){var raw=UUID.randomUUID().toString();byte[] text=raw.getBytes(StandardCharsets.UTF_8),mask=new byte[text.length],out=new byte[text.length*2];new java.security.SecureRandom().nextBytes(mask);System.arraycopy(mask,0,out,0,mask.length);for(int i=0;i<text.length;i++)out[text.length+i]=(byte)(text[i]^mask[i]);return new String[]{cookie(id)+"; __Host-FDU_CSRF="+raw,Base64.getUrlEncoder().encodeToString(out)};}
    void smtpOverride(String host,int port){context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("notification-test-smtp",Map.of("campus.notifications.smtp.host",host,"campus.notifications.smtp.port",port)));}
    JsonNode messages()throws Exception {var r=http.send(HttpRequest.newBuilder(URI.create(inbox+"/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,r.statusCode(),"real Mailpit inbox unavailable");return json.readTree(r.body());}
    int mailCount(String address)throws Exception {int count=0;for(var m:messages().path("messages"))for(var to:m.path("To"))if(address.equals(to.path("Address").asText()))count++;return count;}
    @Test void ownsOnlyUnreadAndIdempotentRead()throws Exception {
        String own=accepted(event(user1,Instant.now().minusSeconds(1),null)).path("notificationId").asText();
        String other=accepted(event(user2,Instant.now().minusSeconds(1),null)).path("notificationId").asText();dispatch();
        var r=request("GET","/api/notifications?recipientId="+user2,null,cookie(user1),null,null);assertEquals(200,r.statusCode());
        for(var n:json.readTree(r.body()).path("items"))assertEquals(user1.toString(),n.path("recipientId").asText());
        assertFalse(item(user1,own).path("title").asText().isBlank());
        var english=http.send(HttpRequest.newBuilder(URI.create(base+"/api/notifications?size=100")).header("Cookie",cookie(user1)).header("Accept-Language","en").GET().build(),HttpResponse.BodyHandlers.ofString());
        var nodes=json.readTree(english.body()).path("items");assertEquals("en",nodes.get(0).path("locale").asText());assertEquals("Study reminder",nodes.get(0).path("title").asText());
        String[] xsrf=csrf(user1);assertEquals(204,request("POST","/api/notifications/"+own+"/read",null,xsrf[0],null,xsrf[1]).statusCode());String first=item(user1,own).path("readAt").asText();assertFalse(first.isBlank());
        assertEquals(204,request("POST","/api/notifications/"+own+"/read",null,xsrf[0],null,xsrf[1]).statusCode());assertEquals(first,item(user1,own).path("readAt").asText());
        assertEquals(404,request("POST","/api/notifications/"+other+"/read",null,xsrf[0],null,xsrf[1]).statusCode());assertEquals(404,request("POST","/api/notifications/"+UUID.randomUUID()+"/read",null,xsrf[0],null,xsrf[1]).statusCode());
    }
    @Test void rejectsUnauthenticatedForbiddenAndCsrf()throws Exception {
        String id=accepted(event(user1,Instant.now().minusSeconds(1),null)).path("notificationId").asText();dispatch();
        assertEquals(401,request("GET","/api/notifications",null,null,null,null).statusCode());assertEquals(403,request("GET","/api/notifications",null,cookie(denied),null,null).statusCode());
        assertEquals(403,request("POST","/api/notifications/"+id+"/read",null,cookie(user1),null,null).statusCode());assertTrue(item(user1,id).path("readAt").isNull());permit.set(false);assertEquals(403,request("GET","/api/notifications",null,cookie(user1),null,null).statusCode());permit.set(true);
        assertEquals(401,request("POST","/internal/notifications/events",event(user1,Instant.now(),null),cookie(user1),null,null).statusCode());
        assertEquals(401,request("POST","/internal/notifications/events",event(user1,Instant.now(),null),null,"wrong",null).statusCode());
        assertEquals(401,request("GET","/internal/unrelated",null,null,KEY,null).statusCode());
    }
    @Test void deduplicatesAndRejectsChangedPayload()throws Exception {
        var e=event(user1,Instant.now().minusSeconds(1),null);var first=accepted(e);var retry=request("POST","/internal/notifications/events",e,null,KEY,null);assertEquals(200,retry.statusCode());var again=json.readTree(retry.body());assertTrue(again.path("duplicate").asBoolean());assertEquals(first.path("notificationId"),again.path("notificationId"));
        e.put("body","different");assertEquals(409,request("POST","/internal/notifications/events",e,null,KEY,null).statusCode());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE event_id=?",Integer.class,e.get("eventId")));
    }
    @Test void notConfiguredNeverClaimsDelivery()throws Exception {
        smtpOverride("",smtpPort);String id=accepted(event(user1,Instant.now().minusSeconds(1),"missing-"+UUID.randomUUID()+"@example.test")).path("notificationId").asText();dispatch();var n=item(user1,id);assertEquals("SENT",n.path("status").asText());assertEquals("NOT_CONFIGURED",n.path("emailStatus").asText());assertNotEquals("SENT",n.path("emailStatus").asText());
        assertNull(jdbc.queryForObject("SELECT sent_at FROM support.notifications WHERE event_id=(SELECT event_id FROM support.notifications WHERE id=?) AND channel='EMAIL'",java.sql.Timestamp.class,UUID.fromString(id)));
    }
    @Test void schedulesAndCancelsWithoutErasingSent()throws Exception {
        var future=event(user1,Instant.now().plusSeconds(3600),"cancel-"+UUID.randomUUID()+"@example.test");var f=accepted(future);assertEquals("PENDING",f.path("inAppStatus").asText());
        String path="/internal/notifications/events/"+future.get("eventId")+"/cancel";assertEquals(200,request("POST",path,null,null,KEY,null).statusCode());assertEquals(200,request("POST",path,null,null,KEY,null).statusCode());dispatch();
        assertEquals("CANCELLED",item(user1,f.path("notificationId").asText()).path("status").asText());assertEquals(0,mailCount(future.get("emailAddress").toString()));
        var past=event(user1,Instant.now().minusSeconds(1),null);var p=accepted(past);dispatch();request("POST","/internal/notifications/events/"+past.get("eventId")+"/cancel",null,null,KEY,null);assertEquals("SENT",item(user1,p.path("notificationId").asText()).path("status").asText());
        assertEquals(404,request("POST","/internal/notifications/events/"+UUID.randomUUID()+"/cancel",null,null,KEY,null).statusCode());
    }
    @Test void actuallyDeliversToMailpit()throws Exception {
        String address="real-"+UUID.randomUUID()+"@example.test";String id=accepted(event(user1,Instant.now().minusSeconds(1),address)).path("notificationId").asText();dispatch();assertEquals("SENT",item(user1,id).path("emailStatus").asText());assertEquals(1,mailCount(address));
        JsonNode found=null;for(var m:messages().path("messages"))for(var to:m.path("To"))if(address.equals(to.path("Address").asText()))found=m;assertNotNull(found);
        var detail=http.send(HttpRequest.newBuilder(URI.create(inbox+"/api/v1/message/"+found.path("ID").asText())).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,detail.statusCode());var mail=json.readTree(detail.body());assertEquals("自习提醒",mail.path("Subject").asText());assertTrue(mail.path("Text").asText().contains("H3101 09:00-10:00"));dispatch();assertEquals(1,mailCount(address));
        // RFC 2047 section 2: each encoded-word <= 75 and each physical encoded header line <= 76.
        String title="自习提醒😀".repeat(30),longAddress="long-"+UUID.randomUUID()+"@example.test";
        var longEvent=event(user1,Instant.now().minusSeconds(1),longAddress);longEvent.put("title",title);accepted(longEvent);dispatch();
        JsonNode longMail=null;for(var m:messages().path("messages"))for(var to:m.path("To"))if(longAddress.equals(to.path("Address").asText()))longMail=m;assertNotNull(longMail);
        var raw=http.send(HttpRequest.newBuilder(URI.create(inbox+"/api/v1/message/"+longMail.path("ID").asText()+"/raw")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,raw.statusCode());
        StringBuilder decoded=new StringBuilder();boolean subject=false;
        for(String line:raw.body().split("\\r?\\n")){
            if(line.startsWith("Subject:"))subject=true;else if(subject&&!line.startsWith(" ")&&!line.startsWith("\t"))break;
            if(!subject)continue;assertTrue(line.length()<=76,"RFC2047 physical subject line must be at most 76 characters");
            var words=java.util.regex.Pattern.compile("=\\?UTF-8\\?B\\?([A-Za-z0-9+/=]+)\\?=",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(line);
            while(words.find()){assertTrue(words.group().length()<=75,"RFC2047 encoded-word must be at most 75 characters");byte[] bytes=Base64.getDecoder().decode(words.group(1));decoded.append(StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)));}
        }
        assertEquals(title,decoded.toString(),"folded encoded words preserve every Unicode code point");
    }
    @Test void explicitFailureRetriesWithSameMessage()throws Exception {
        String address="retry-"+UUID.randomUUID()+"@example.test";String id=accepted(event(user1,Instant.now().minusSeconds(1),address)).path("notificationId").asText();
        try(var fault=new FaultSmtp(false)){smtpOverride("127.0.0.1",fault.port());dispatch();}
        var n=item(user1,id);assertEquals("FAILED",n.path("emailStatus").asText());assertFalse(n.path("lastErrorCode").asText().isBlank());assertFalse(n.path("nextRetryAt").isNull());assertEquals(0,mailCount(address));
        context.getEnvironment().getPropertySources().remove("notification-test-smtp");jdbc.update("UPDATE support.notifications SET next_retry_at=now()-interval '1 second' WHERE event_id=(SELECT event_id FROM support.notifications WHERE id=?) AND channel='EMAIL'",UUID.fromString(id));dispatch();assertEquals("SENT",item(user1,id).path("emailStatus").asText());assertEquals(1,mailCount(address));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE event_id=(SELECT event_id FROM support.notifications WHERE id=?)",Integer.class,UUID.fromString(id)));
    }
    @Test void uncertainAcceptanceNeverAutomaticallyRetries()throws Exception {
        String id=accepted(event(user1,Instant.now().minusSeconds(1),"uncertain-"+UUID.randomUUID()+"@example.test")).path("notificationId").asText();
        try(var fault=new FaultSmtp(true)){smtpOverride("127.0.0.1",fault.port());dispatch();assertTrue(fault.received.get());}
        var n=item(user1,id);assertEquals("UNKNOWN",n.path("emailStatus").asText());assertTrue(n.path("nextRetryAt").isNull());int before=jdbc.queryForObject("SELECT retry_count FROM support.notifications WHERE event_id=(SELECT event_id FROM support.notifications WHERE id=?) AND channel='EMAIL'",Integer.class,UUID.fromString(id));context.getEnvironment().getPropertySources().remove("notification-test-smtp");dispatch();dispatch();assertEquals("UNKNOWN",item(user1,id).path("emailStatus").asText());assertEquals(before,jdbc.queryForObject("SELECT retry_count FROM support.notifications WHERE event_id=(SELECT event_id FROM support.notifications WHERE id=?) AND channel='EMAIL'",Integer.class,UUID.fromString(id)));
    }
    @Test void databaseConstraintsAndRestartPersistence()throws Exception {
        var e=event(user1,Instant.now().minusSeconds(1),null);String id=accepted(e).path("notificationId").asText();dispatch();
        try(var connection=context.getBean(javax.sql.DataSource.class).getConnection();var stmt=connection.prepareStatement("SELECT recipient_id,status FROM support.notifications WHERE id=?")){stmt.setObject(1,UUID.fromString(id));try(var rows=stmt.executeQuery()){assertTrue(rows.next());assertEquals(user1,rows.getObject(1,UUID.class));assertEquals("SENT",rows.getString(2));}}
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE support.notifications SET retry_count=-1 WHERE id=?",UUID.fromString(id)));assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE support.notifications SET status='INVALID' WHERE id=?",UUID.fromString(id)));assertEquals("SENT",item(user1,id).path("status").asText());
    }
    @Test void raceDuplicateEventsStillSingleRecord()throws Exception {
        var e=event(user1,Instant.now().minusSeconds(1),null);String id=accepted(e).path("notificationId").asText();
        try(var pool=Executors.newFixedThreadPool(4)){var tasks=new ArrayList<Callable<String>>();for(int i=0;i<8;i++)tasks.add(()->{var r=request("POST","/internal/notifications/events",e,null,KEY,null);assertEquals(200,r.statusCode(),r.body());return json.readTree(r.body()).path("notificationId").asText();});for(var result:pool.invokeAll(tasks))assertEquals(id,result.get());}
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE event_id=?",Integer.class,e.get("eventId")));
    }
    static final class FaultSmtp implements AutoCloseable {
        final ServerSocket server;final AtomicBoolean received=new AtomicBoolean();final Thread worker;
        FaultSmtp(boolean uncertain)throws IOException{server=new ServerSocket(0,5,InetAddress.getLoopbackAddress());worker=Thread.ofPlatform().daemon().start(()->{
            try(var socket=server.accept()){socket.setSoTimeout(5000);var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));var out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
                if(!uncertain){out.print("421 Temporarily unavailable\r\n");out.flush();return;}
                out.print("220 test SMTP\r\n");out.flush();String line;boolean data=false;while((line=in.readLine())!=null){if(data){if(line.equals(".")){received.set(true);return;}continue;}if(line.startsWith("DATA")){out.print("354 Send data\r\n");data=true;}else out.print("250 OK\r\n");out.flush();}
            }catch(IOException ignored){}
        });}
        int port(){return server.getLocalPort();}public void close()throws Exception{server.close();worker.join(1000);}
    }
}
