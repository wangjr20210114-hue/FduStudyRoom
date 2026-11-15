package com.campus.notification;

import com.campus.WorkflowApplication;
import com.campus.common.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
public abstract class SupportHttpFixture {
 protected static final String KEY="support-contract-test-service-key-00000000000000";
 protected final JsonMapper json=JsonMapper.builder().build();
 protected final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 protected final UUID user=UUID.randomUUID(),denied=UUID.randomUUID(),room=UUID.randomUUID();
 protected volatile boolean permit=true;protected volatile int sourceStatus=200;
 protected volatile boolean wrongScope=false,brokenSource=false,history=true,zero=false;
 protected final List<String> sourceRequests=new CopyOnWriteArrayList<>();
 protected ConfigurableApplicationContext app;protected HttpServer upstream;protected String base;protected JdbcTemplate jdbc;protected TokenService tokens;
 protected final Instant from=Instant.parse("2030-06-03T00:00:00Z"),to=Instant.parse("2030-06-03T04:00:00Z"),asOf=Instant.parse("2030-06-03T05:00:00Z");
 @BeforeAll void startFixture()throws Exception {
  var sign=Base64.getEncoder().encodeToString(new byte[32]);tokens=new TokenService(new AuthSettings("password",sign,KEY,true,Duration.ofMinutes(30)));
  upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  upstream.createContext("/introspect",e->{
   var c=tokens.verify(e.getRequestHeaders().getFirst("Authorization").substring(7));
   var permissions=permit&&!c.userId().equals(denied)?Set.of("audit:read","statistics:read","notification:read"):Set.<String>of();
   respond(e,200,new IdentityPrincipal(c.userId(),"fixture","Fixture","USER",permissions,1,false));
  });
  upstream.createContext("/internal/resources/statistics",e->source(e,true));
  upstream.createContext("/internal/reservations/statistics",e->source(e,false));upstream.start();
  String service="http://127.0.0.1:"+upstream.getAddress().getPort();
  app=new SpringApplicationBuilder(WorkflowApplication.class).run("--server.address=127.0.0.1","--server.port=0","--spring.datasource.url="+System.getenv("CAMPUS_DB_URL"),"--spring.datasource.username="+System.getenv("CAMPUS_DB_USER"),"--spring.datasource.password="+System.getenv("CAMPUS_DB_PASSWORD"),"--campus.auth.mode=password","--campus.auth.signing-key="+sign,"--campus.auth.service-key="+KEY,"--campus.auth.secure-cookies=true","--campus.workflow.identity-url="+service+"/introspect","--campus.notifications.initial-delay-ms=3600000","--campus.notifications.smtp.host=smtp","--campus.notifications.smtp.port=1025","--campus.notifications.smtp.from=studyroom@example.test","--campus.notifications.smtp.allow-insecure=true");
  base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");jdbc=app.getBean(JdbcTemplate.class);
 }
 @AfterAll void stopFixture(){
  try{if(app!=null)app.close();}
  finally{
   try{if(upstream!=null)upstream.stop(0);}
   finally{http.close();}
  }
 }
 @BeforeEach void resetFixture(){permit=true;sourceStatus=200;wrongScope=false;brokenSource=false;history=true;zero=false;sourceRequests.clear();app.getEnvironment().getPropertySources().remove("support-overrides");}
 protected void overrides(Map<String,Object> values){app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("support-overrides",values));}
 protected void respond(com.sun.net.httpserver.HttpExchange e,int status,Object body)throws java.io.IOException {
  byte[] b=json.writeValueAsBytes(body);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close();
 }
 private void source(com.sun.net.httpserver.HttpExchange e,boolean resource)throws java.io.IOException {
  if(!KEY.equals(e.getRequestHeaders().getFirst("X-Service-Key"))){respond(e,401,Map.of());return;}
  sourceRequests.add(e.getRequestURI().toString());
  if(sourceStatus!=200){respond(e,sourceStatus,Map.of());return;}
  var params=new HashMap<String,String>();for(String part:e.getRequestURI().getRawQuery().split("&")){var pair=part.split("=",2);params.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
  var out=new LinkedHashMap<String,Object>();out.put("from",wrongScope?from.plusSeconds(1).toString():params.get("from"));out.put("to",params.get("to"));out.put("asOf",params.get("asOf"));out.put("roomId",params.get("roomId"));out.put("algorithm",resource?"retained-seat-seconds-v1":"deadline-cohort-v1");
  if(resource){
   out.put("historyComplete",history);out.put("historyAvailableFrom",history?from.minusSeconds(3600).toString():from.plusSeconds(1).toString());
   for(var entry:Map.of("capacitySeatSeconds",zero?0L:28800L,"occupiedSeatSeconds",zero?0L:10800L,"occupiedM1SeatSeconds",zero?0L:3600L,"occupiedM2SeatSeconds",zero?0L:7200L,"confirmedBookingCount",zero?0L:2L,"confirmedM1BookingCount",zero?0L:1L,"confirmedM2BookingCount",zero?0L:1L).entrySet())out.put(entry.getKey(),history?entry.getValue():null);
   if(brokenSource)out.put("capacitySeatSeconds",-1);
  }else{out.put("checkedInCount",zero?0:1);out.put("noShowCount",zero?0:1);out.put("excludedBeforeStartCancelledCount",1);out.put("excludedSystemTerminatedCount",1);out.put("notDueCount",2);}
  respond(e,200,out);
 }
 protected String cookie(UUID id){return "__Host-FDU_SESSION="+tokens.issue(id,1);}
 protected HttpResponse<String> request(String method,String path,Object body,String cookie,String key)throws Exception {
  var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(12));if(cookie!=null)b.header("Cookie",cookie);if(key!=null)b.header("X-Service-Key",key);if(body!=null)b.header("Content-Type","application/json");
  return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
 }
 protected JsonNode require(HttpResponse<String> response,int status)throws Exception{assertEquals(status,response.statusCode(),"Required business HTTP behavior: "+response.body());return json.readTree(response.body());}
 protected JsonNode get(String path)throws Exception{return require(request("GET",path,null,cookie(user),null),200);}
 protected JsonNode mailList()throws Exception{return json.readTree(http.send(HttpRequest.newBuilder(URI.create("http://smtp:8025/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());}
 protected int mailCount(String address)throws Exception{int count=0;for(var m:mailList().path("messages"))for(var to:m.path("To"))if(address.equals(to.path("Address").asText()))count++;return count;}
 protected JsonNode mail(String address)throws Exception{for(var m:mailList().path("messages"))for(var to:m.path("To"))if(address.equals(to.path("Address").asText()))return json.readTree(http.send(HttpRequest.newBuilder(URI.create("http://smtp:8025/api/v1/message/"+m.path("ID").asText())).GET().build(),HttpResponse.BodyHandlers.ofString()).body());fail("Real SMTP message missing");return null;}
}
