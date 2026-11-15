package com.campus.identity.api;
import com.campus.ResourceApplication;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;import org.springframework.boot.SpringApplication;import org.springframework.context.ConfigurableApplicationContext;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IdentityHttpFixture {
 static final JsonMapper JSON=JsonMapper.builder().build();static final String PASSWORD="isolated-identity-http-password";
 static final String SERVICE="isolated-identity-service-key-1234567890";
 ConfigurableApplicationContext app;JdbcTemplate jdbc;HttpClient admin;String base;HttpServer notification;
 ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs=new ch.qos.logback.core.read.ListAppender<>();
 MutableClock clock=new MutableClock();AtomicReference<JsonNode> posted=new AtomicReference<>();int calls;String delivery="ACCEPTED";
 @BeforeAll void start()throws Exception{
  logs.start();((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);
  notification=HttpServer.create(new InetSocketAddress("127.0.0.1",8081),0);
  notification.createContext("/internal/notifications/verification-email",e->{
   assertThat(e.getRequestHeaders().getFirst("X-Service-Key")).isEqualTo(SERVICE);
   JsonNode body=JSON.readTree(new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));posted.set(body);calls++;
   Map<String,Object> out=map("operationId",body.get("operationId").asText(),"status",delivery,"duplicate",calls>1,"retryable",!delivery.equals("ACCEPTED")&&!delivery.equals("UNKNOWN"),"nextRetryAt",null,"errorCode",delivery.equals("ACCEPTED")?null:"SAFE_DELIVERY_STATE");
   byte[] bytes=JSON.writeValueAsBytes(out);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(delivery.equals("UNKNOWN")?202:200,bytes.length);e.getResponseBody().write(bytes);e.close();
  });notification.start();
  String name="admin-"+UUID.randomUUID().toString().substring(0,8);SpringApplication a=new SpringApplication(ResourceApplication.class);
  a.addInitializers(c->c.getBeanFactory().registerSingleton("identityClock",clock));
  app=a.run("--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=0","--campus.auth.secure-cookies=false","--campus.auth.signing-key="+Base64.getEncoder().encodeToString(new byte[32]),"--campus.auth.service-key="+SERVICE,"--campus.bootstrap-admin.login-name="+name,"--campus.bootstrap-admin.password="+PASSWORD);
  logs.list.clear();logs.start();((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);org.slf4j.LoggerFactory.getLogger(IdentityHttpFixture.class).info("identity-log-observer-ready");assertThat(logs.list).as("real log observer is attached after Boot logging reconfiguration").anyMatch(e->e.getFormattedMessage().equals("identity-log-observer-ready"));
  jdbc=app.getBean(JdbcTemplate.class);base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");admin=login(name);
 }
 @BeforeEach void resetClock(){clock.now=Instant.parse("2030-06-03T00:00:00Z");posted.set(null);calls=0;delivery="ACCEPTED";}
 @AfterAll void close(){if(app!=null)app.close();if(notification!=null)notification.stop(0);((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);logs.stop();}
 static class MutableClock extends Clock {volatile Instant now=Instant.parse("2030-06-03T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
 record User(UUID id,String login,HttpClient client){}
 User user()throws Exception{String name="user-"+UUID.randomUUID().toString().substring(0,8);UUID role=jdbc.queryForObject("SELECT id FROM iam.roles WHERE code='USER'",UUID.class);UUID id=UUID.randomUUID();jdbc.update("INSERT INTO iam.users(id,login_name,display_name,password_hash,role_id) VALUES(?,?,?,?,?)",id,name,name,app.getBean(PasswordEncoder.class).encode(PASSWORD),role);return new User(id,name,login(name));}
 HttpClient login(String name)throws Exception{HttpClient c=fresh();assertThat(req(c,"POST","/api/auth/login",map("loginName",name,"password",PASSWORD)).statusCode()).isEqualTo(200);return c;}
 static HttpClient fresh(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();}
 HttpResponse<String> req(HttpClient client,String method,String path,Object body)throws Exception{
  HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15));
  if(!method.equals("GET")){JsonNode c=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());b.header(c.get("headerName").asText(),c.get("token").asText());}
  b.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 HttpResponse<String> internal(String path,String key)throws Exception{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).GET();if(key!=null)b.header("X-Service-Key",key);return fresh().send(b.build(),HttpResponse.BodyHandlers.ofString());}
 static JsonNode tree(HttpResponse<String> r){return JSON.readTree(r.body());}
 static Map<String,Object> map(Object...items){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<items.length;i+=2)m.put((String)items[i],items[i+1]);return m;}
}
