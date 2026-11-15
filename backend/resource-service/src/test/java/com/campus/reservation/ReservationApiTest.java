package com.campus.reservation;

import com.campus.ResourceApplication;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real a+b HTTP and PostgreSQL. f is an isolated idempotent HTTP fixture with real TCP SMTP inbox. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationApiTest {
 static final JsonMapper JSON=JsonMapper.builder().build();
 static final String KEY="reservation-round3-internal-key-32-bytes",PASS="reservation-fixture-password";
 static final TestClock CLOCK=new TestClock();
 static class TestClock extends Clock {volatile Instant now=Instant.parse("2030-06-03T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}}
 /** Test-only upstream isolation: actual a responses, serialized eligibility reads only. */
 static final class EligibilityFixtureGate extends org.springframework.web.filter.OncePerRequestFilter implements org.springframework.core.Ordered {
  private final java.util.concurrent.locks.ReentrantLock gate=new java.util.concurrent.locks.ReentrantLock(true);
  public int getOrder(){return -102;}
  @Override protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request){return !request.getMethod().equals("GET")||!request.getRequestURI().startsWith("/internal/identity/users/")||!request.getRequestURI().endsWith("/eligibility");}
  @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException {gate.lock();try{chain.doFilter(request,response);}finally{gate.unlock();}}
 }
 @TestConfiguration(proxyBeanMethods=false) static class Fixture {
  @Bean EligibilityFixtureGate reservationEligibilityFixtureGate(){return new EligibilityFixtureGate();}
  @Bean @Primary @Qualifier("reservationReminderClock") Clock reservationTestClock(){return CLOCK;}
  @Bean @Primary @Qualifier("resourceCheckinClock") Clock resourceTestClock(){return CLOCK;}
 }
 ConfigurableApplicationContext app;JdbcTemplate db;String base;HttpClient admin,user,other,internal;
 UUID userId,otherId,room,seat,seat2,otherRoom,otherSeat;
 HttpServer notifications,resources;ServerSocket smtp;ExecutorService smtpPool;
 final List<HttpClient> clients=new CopyOnWriteArrayList<>();
 final List<Map<String,Object>> events=new CopyOnWriteArrayList<>();
 final Map<String,Map<String,Object>> logical=new ConcurrentHashMap<>();
 final List<String> inbox=new CopyOnWriteArrayList<>();
 final Map<String,String> tokens=new ConcurrentHashMap<>();
 volatile boolean notificationFailure,loseNotificationAck,unknownMail,loseAllocateAck,failRelease,resourceFailure;
 @BeforeAll void start()throws Exception {
  smtp=new ServerSocket(0,20,InetAddress.getLoopbackAddress());smtpPool=Executors.newCachedThreadPool();
  smtpPool.submit(()->{while(!smtp.isClosed())try{Socket s=smtp.accept();smtpPool.submit(()->receiveSmtp(s));}catch(IOException ignored){}});
  notifications=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);notifications.setExecutor(Executors.newCachedThreadPool());
  notifications.createContext("/internal/",e->{try{
   if(!KEY.equals(e.getRequestHeaders().getFirst("X-Service-Key"))){e.sendResponseHeaders(401,-1);return;}
   String path=e.getRequestURI().getPath();Map<String,Object>b=Map.of();if(!path.endsWith("/cancel"))b=JSON.readValue(e.getRequestBody().readAllBytes(),Map.class);
   Map<String,Object>response;
   if(path.endsWith("verification-email")){
    tokens.put(b.get("userId").toString(),b.get("token").toString());response=map("operationId",b.get("operationId"),"status","ACCEPTED","duplicate",false,"retryable",false,"nextRetryAt",null,"errorCode",null);
   }else if(path.contains("audit")){response=map("eventKey",b.get("eventKey"),"duplicate",false);
   }else if(path.endsWith("/cancel")){String id=path.split("/")[4];response=map("cancelled",true);
   }else{
    events.add(b);if(notificationFailure){e.sendResponseHeaders(503,-1);return;}
    String id=b.get("eventId").toString();Map<String,Object>old=logical.putIfAbsent(id,b);
    if(old!=null&&!old.equals(b)){e.sendResponseHeaders(409,-1);return;}
    if(old==null&&b.get("emailAddress")!=null)sendSmtp(b);
    if(loseNotificationAck){e.close();return;}
    response=map("eventId",id,"duplicate",old!=null,"inAppStatus","SENT","emailStatus",b.get("emailAddress")==null?"NOT_CONFIGURED":unknownMail?"UNKNOWN":"SENT");
   }
   byte[]bytes=JSON.writeValueAsBytes(response);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);
  }catch(Exception failure){e.sendResponseHeaders(503,-1);}finally{e.close();}});notifications.start();
  resources=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);resources.setExecutor(Executors.newCachedThreadPool());
  resources.createContext("/internal/resources/",e->{try{if(resourceFailure||(failRelease&&e.getRequestURI().getPath().endsWith("/releases"))){e.sendResponseHeaders(503,-1);return;}var b=HttpRequest.newBuilder(URI.create(base+e.getRequestURI())).timeout(Duration.ofSeconds(10)).header("X-Service-Key",KEY).header("Content-Type","application/json").method(e.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(e.getRequestBody().readAllBytes()));try(var c=HttpClient.newHttpClient()){var r=c.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());if(loseAllocateAck&&e.getRequestURI().getPath().endsWith("/allocations")){e.close();return;}e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(r.statusCode(),r.body().length);e.getResponseBody().write(r.body());}}catch(Exception failure){e.sendResponseHeaders(503,-1);}finally{e.close();}});resources.start();
  String login="c-admin-"+UUID.randomUUID().toString().substring(0,8);
  int servicePort;try(var listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){servicePort=listener.getLocalPort();}
  app=new SpringApplicationBuilder(ResourceApplication.class,Fixture.class).run("--spring.profiles.active=local","--server.address=127.0.0.1","--server.port="+servicePort,"--spring.datasource.hikari.maximum-pool-size=2","--spring.datasource.hikari.connection-timeout=1500","--campus.auth.secure-cookies=false","--campus.auth.signing-key="+Base64.getEncoder().encodeToString(new byte[32]),"--campus.auth.service-key="+KEY,"--campus.bootstrap-admin.login-name="+login,"--campus.bootstrap-admin.password="+PASS,"--campus.reservation.notification-url=http://127.0.0.1:"+notifications.getAddress().getPort(),"--campus.reservation.resource-url=http://127.0.0.1:"+resources.getAddress().getPort(),"--campus.reservation.lifecycle-delay-ms=3600000","--campus.reservation.reminder-delay-ms=3600000");
  db=app.getBean(JdbcTemplate.class);base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");
  admin=client();status(200,call(admin,"POST","/api/auth/login",map("loginName",login,"password",PASS),false));
  user=client();userId=register(user);other=client();otherId=register(other);internal=client();
  status(200,call(user,"PATCH","/api/account/email",map("email","student@example.test"),false));
  status(200,call(user,"POST","/api/account/email/verification",null,false));
  status(200,call(user,"POST","/api/account/email/verify",map("token",tokens.get(userId.toString())),false));
 }
 void receiveSmtp(Socket s){try(s;var r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));var w=new PrintWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8),true)){
  w.print("220 inbox ESMTP\r\n");w.flush();String line;boolean data=false;StringBuilder body=new StringBuilder();
  while((line=r.readLine())!=null){if(data){if(line.equals(".")){inbox.add(body.toString());data=false;w.print("250 queued\r\n");}else body.append(line).append('\n');}else if(line.startsWith("DATA")){data=true;w.print("354 end with dot\r\n");}else if(line.startsWith("QUIT")){w.print("221 bye\r\n");w.flush();break;}else w.print("250 OK\r\n");w.flush();}
 }catch(IOException ignored){}}
 void sendSmtp(Map<String,Object>b)throws Exception {try(Socket s=new Socket(InetAddress.getLoopbackAddress(),smtp.getLocalPort());var r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));var w=new PrintWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8),true)){
  assertTrue(r.readLine().startsWith("220"));for(String cmd:List.of("EHLO fixture","MAIL FROM:<test@example.test>","RCPT TO:<"+b.get("emailAddress")+">","DATA")){w.print(cmd+"\r\n");w.flush();assertNotNull(r.readLine());}
  w.print("Subject: "+b.get("titleEn")+"\r\nTo: "+b.get("emailAddress")+"\r\n\r\n"+b.get("body")+"\r\n"+b.get("bodyEn")+"\r\n.\r\n");w.flush();assertTrue(r.readLine().startsWith("250"));w.print("QUIT\r\n");w.flush();r.readLine();}}
 UUID register(HttpClient c)throws Exception {var r=call(c,"POST","/api/auth/register",map("loginName","c-user-"+UUID.randomUUID().toString().substring(0,10),"password",PASS),false);status(201,r);return UUID.fromString(tree(r).path("id").asText());}
 HttpClient client(){var c=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(3)).build();clients.add(c);return c;}
 static Map<String,Object> map(Object...a){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<a.length;i+=2)m.put((String)a[i],a[i+1]);return m;}
 @BeforeEach void arrange()throws Exception {
  at("08:00:00");loseAllocateAck=false;failRelease=false;resourceFailure=false;notificationFailure=false;loseNotificationAck=false;unknownMail=false;
  if(Boolean.TRUE.equals(db.queryForObject("SELECT to_regclass('m1.seat_reservations') IS NOT NULL",Boolean.class))){
   for(var row:db.queryForList("SELECT id FROM m1.seat_reservations WHERE user_id IN (?,?)",userId,otherId))in("POST","/internal/resources/releases",map("operationId",UUID.randomUUID(),"sourceModule","M1","sourceId",row.get("id"),"sourceVersion",100));
   for(String t:List.of("reservation_outbox","reservation_commands","reservation_violations","reservation_reminders","maintenance_operations","maintenance_sources","reminder_tasks","seat_reservations"))if(Boolean.TRUE.equals(db.queryForObject("SELECT to_regclass(?) IS NOT NULL",Boolean.class,"m1."+t)))db.update("DELETE FROM m1."+t);
  }
  events.clear();logical.clear();inbox.clear();
  settings(4,15,10,15);
  room=createRoom(null);seat=createSeat(room,"A01","FIXED");seat2=createSeat(room,"A02","NONE");
  otherRoom=createRoom(null);otherSeat=createSeat(otherRoom,"B01","FIXED");
 }
 void settings(int max,int before,int after,int deadline)throws Exception {var old=call(admin,"GET","/api/admin/settings",null,false);status(200,old);status(200,call(admin,"PUT","/api/admin/settings",map("expectedVersion",tree(old).path("version").asInt(),"maxReservationHours",max,"beforeStartReminderMinutes",before,"afterStartReminderMinutes",after,"checkinDeadlineMinutes",deadline,"approvalTtlMinutes",60,"repairSlaMinutes",120),false));}
 UUID createRoom(String dept)throws Exception {String code="C"+UUID.randomUUID().toString().substring(0,10);var r=call(admin,"POST","/api/resources/rooms",map("code",code,"name",code,"campus","Handan","building","A","capacity",4,"openTime","08:00","closeTime","22:00","departmentCode",dept,"bookingEnabled",true,"enabled",true),false);status(201,r);return UUID.fromString(tree(r).path("id").asText());}
 UUID createSeat(UUID r,String code,String socket)throws Exception {var x=call(admin,"POST","/api/resources/rooms/"+r+"/seats",map("code",code,"socketType",socket,"enabled",true),false);status(201,x);return UUID.fromString(tree(x).path("id").asText());}
 String time(String hhmm){return "2030-06-03T"+hhmm+":00+08:00";}
 void at(String value){CLOCK.now=OffsetDateTime.parse("2030-06-03T"+value+"+08:00").toInstant();}
 HttpResponse<String> call(HttpClient c,String verb,String path,Object body,boolean service)throws Exception {
  var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25)).header("Content-Type","application/json");if(service)b.header("X-Service-Key",KEY);
  else if(!verb.equals("GET")){var csrf=c.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());var n=tree(csrf);b.header(n.path("headerName").asText(),n.path("token").asText());}
  return c.send(b.method(verb,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
 }
 HttpResponse<String> in(String method,String path,Object body)throws Exception{return call(internal,method,path,body,true);}
 JsonNode tree(HttpResponse<String>r){return JSON.readTree(r.body());}
 void status(int n,HttpResponse<String>r){assertEquals(n,r.statusCode(),"reservation contract response: "+r.body());}
 Map<String,Object> booking(UUID s,String start,String end){return map("operationId",UUID.randomUUID(),"seatId",s,"startsAt",time(start),"endsAt",time(end));}
 HttpResponse<String> post(HttpClient c,Map<String,Object>b)throws Exception{return call(c,"POST","/api/reservations",b,false);}
 String reserve(HttpClient c,UUID s,String start,String end)throws Exception{var r=post(c,booking(s,start,end));status(201,r);assertEquals("RESERVED",tree(r).path("status").asText(),"reservation must await actual allocation confirmation");return tree(r).path("id").asText();}
 JsonNode get(HttpClient c,String id)throws Exception{var r=call(c,"GET","/api/reservations/"+id,null,false);status(200,r);return tree(r);}
 HttpResponse<String> cancel(HttpClient c,String id,UUID op)throws Exception{return call(c,"POST","/api/reservations/"+id+"/cancel",map("operationId",op),false);}
 void tick()throws Exception {assertTrue(app.containsBean("reservationService"),"reservation lifecycle worker must exist");Object s=app.getBean("reservationService");try{s.getClass().getMethod("tick").invoke(s);}catch(java.lang.reflect.InvocationTargetException e){throw (Exception)e.getCause();}}
 String code(UUID r)throws Exception{var x=call(admin,"GET","/api/resources/rooms/"+r+"/checkin-code",null,false);status(200,x);return tree(x).path("code").asText();}
 HttpResponse<String> check(HttpClient c,String id,String code)throws Exception{return call(c,"POST","/api/reservations/"+id+"/check-in",map("operationId",UUID.randomUUID(),"code",code),false);}
 String search(UUID userRoom,boolean socket){return "/api/reservations/availability?startsAt="+enc(time("09:00"))+"&endsAt="+enc(time("10:00"))+"&campus=Handan&building=A&hasSocket="+socket;}
 String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
 List<Map<String,Object>> beforeEvents(){return logical.values().stream().filter(e->"预约提醒".equals(e.get("title"))).toList();}
 long violations(String id){return db.queryForObject("SELECT count(*) FROM m1.reservation_violations WHERE reservation_id=?::uuid",Long.class,id);}
 @Test void m101Filters()throws Exception {var r=call(user,"GET",search(room,true),null,false);status(200,r);assertTrue(r.body().contains(seat.toString()));assertFalse(r.body().contains(seat2.toString()));assertEquals(0,tree(call(user,"GET",search(room,true)+"&roomId="+UUID.randomUUID(),null,false)).size());}
 @Test void m101Restrictions()throws Exception {reserve(user,seat,"09:00","10:00");UUID dept=createRoom("CS"),ds=createSeat(dept,"CS1","FIXED");UUID repair=UUID.randomUUID();status(201,in("PUT","/internal/resources/restrictions/"+repair,map("operationId",UUID.randomUUID(),"sourceVersion",1,"roomId",otherRoom,"active",true)));var r=call(other,"GET",search(room,true),null,false);status(200,r);assertFalse(r.body().contains(seat.toString()));assertFalse(r.body().contains(ds.toString()));assertFalse(r.body().contains(otherSeat.toString()));}
 @Test void m101UnavailableDifferentFromEmpty()throws Exception {var r=call(user,"GET",search(room,true).replace("campus=Handan","campus=Nowhere"),null,false);status(200,r);assertEquals(0,tree(r).size());status(400,call(user,"GET","/api/reservations/availability?startsAt=bad&endsAt=bad",null,false));resourceFailure=true;status(503,call(user,"GET",search(room,true),null,false));}
 @Test void m102Hours()throws Exception {reserve(user,seat,"09:00","13:00");status(400,post(other,booking(seat2,"09:30","10:30")));status(400,post(other,booking(seat2,"09:00","14:00")));status(400,post(other,booking(seat2,"10:00","09:00")));}
 @Test void m102DynamicAndOpening()throws Exception {settings(2,15,5,15);at("08:01:00");status(400,post(user,booking(seat,"08:00","09:00")));status(400,post(user,booking(seat,"09:00","12:00")));status(409,post(user,booking(seat,"21:00","23:00")));String id=reserve(user,seat,"09:00","11:00");assertEquals(2,get(user,id).path("policySnapshot").path("maxReservationHours").asInt());settings(1,20,5,10);assertTrue(get(user,id).path("checkinDeadlineAt").asText().contains("01:15:00"));}
 @Test void m102Concurrent()throws Exception {ExecutorService pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->post(user,booking(seat,"09:00","10:00")));var b=pool.submit(()->post(other,booking(seat,"09:00","10:00")));var ra=a.get();var rb=b.get();assertEquals(Set.of(201,409),new HashSet<>(List.of(ra.statusCode(),rb.statusCode())),"reservation contract response: concurrent database exclusion must allow exactly one booking");}finally{pool.shutdownNow();}}
 @Test void m102Idempotency()throws Exception {var b=booking(seat,"09:00","10:00");var a=post(user,b);status(201,a);var same=post(user,b);status(200,same);assertEquals(tree(a).path("id"),tree(same).path("id"));b.put("endsAt",time("11:00"));status(409,post(user,b));}
 @Test void m102UnknownConfirmationRecovery()throws Exception {loseAllocateAck=true;var body=booking(seat,"09:00","10:00");var response=post(user,body);status(202,response);String id=tree(response).path("id").asText();assertEquals("PENDING",tree(response).path("status").asText());loseAllocateAck=false;at("08:00:31");tick();assertEquals("RESERVED",get(user,id).path("status").asText());var duplicate=post(user,body);status(200,duplicate);assertEquals(id,tree(duplicate).path("id").asText());assertEquals(1,tree(in("GET","/internal/resources/sources/M1/"+id,null)).path("sourceVersion").asInt());}
 @Test void m102DependencyFailure()throws Exception {resourceFailure=true;status(503,post(user,booking(seat,"09:00","10:00")));assertEquals(0,db.queryForObject("SELECT count(*) FROM m1.seat_reservations WHERE user_id=?",Integer.class,userId));}
 @Test void m103OwnerAndIdempotency()throws Exception {String id=reserve(user,seat,"09:00","10:00");UUID op=UUID.randomUUID();status(200,cancel(user,id,op));status(200,cancel(user,id,op));assertEquals("CANCELLED",get(user,id).path("status").asText());assertFalse(tree(in("GET","/internal/resources/sources/M1/"+id,null)).path("active").asBoolean());}
 @Test void m103NonownerAndAdmin()throws Exception {String id=reserve(user,seat,"09:00","10:00");status(403,cancel(other,id,UUID.randomUUID()));status(400,call(admin,"POST","/api/reservations/"+id+"/admin-cancel",map("operationId",UUID.randomUUID(),"reason",""),false));at("09:00:00");status(200,call(admin,"POST","/api/reservations/"+id+"/admin-cancel",map("operationId",UUID.randomUUID(),"reason","Room closed"),false));assertEquals("ADMIN",get(user,id).path("cancelReason").asText());}
 @Test void m103InvalidState()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("09:00:00");status(409,cancel(user,id,UUID.randomUUID()));status(200,check(user,id,code(room)));status(409,cancel(user,id,UUID.randomUUID()));}
 @Test void m103ReleasePreservesOtherSource()throws Exception {String id=reserve(user,seat,"09:00","10:00");UUID work=UUID.randomUUID();status(201,in("PUT","/internal/resources/restrictions/"+work,map("operationId",UUID.randomUUID(),"sourceVersion",1,"roomId",room,"active",true)));status(200,cancel(user,id,UUID.randomUUID()));assertTrue(tree(in("GET","/internal/resources/sources/M3/"+work,null)).path("active").asBoolean());status(409,post(other,booking(seat,"09:00","10:00")));}
 @Test void m103ReleaseRetry()throws Exception {String id=reserve(user,seat,"09:00","10:00");failRelease=true;var r=cancel(user,id,UUID.randomUUID());status(202,r);assertEquals("CANCEL_PENDING",get(user,id).path("status").asText());failRelease=false;at("08:00:31");tick();assertEquals("CANCELLED",get(user,id).path("status").asText());assertFalse(tree(in("GET","/internal/resources/sources/M1/"+id,null)).path("active").asBoolean());}
 @Test void m104OwnCurrentCode()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("09:00:00");status(200,check(user,id,code(room)));assertEquals("CHECKED_IN",get(user,id).path("status").asText());assertNotNull(get(user,id).get("checkedInAt"));assertEquals(0,violations(id));}
 @Test void m104WrongCodeAndOwner()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("09:00:00");status(403,check(other,id,code(room)));String wrong=code(otherRoom);if(wrong.equals(code(room)))wrong="999999".equals(wrong)?"000000":"999999";status(400,check(user,id,wrong));assertEquals("RESERVED",get(user,id).path("status").asText());assertEquals(0,violations(id));}
 @Test void m104WindowAndRepeat()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("08:59:59");status(409,check(user,id,code(room)));at("09:14:59");String c=code(room);status(200,check(user,id,c));status(409,check(user,id,c));at("09:15:00");tick();assertEquals("CHECKED_IN",get(user,id).path("status").asText());assertEquals(0,violations(id));}
 @Test void m104RaceDeadline()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("09:15:00");String c=code(room);ExecutorService pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->check(user,id,c));var b=pool.submit(()->{tick();return true;});status(409,a.get());b.get();}finally{pool.shutdownNow();}assertEquals("NO_SHOW",get(user,id).path("status").asText());assertEquals(1,violations(id));}
 @Test void m105BoundaryAndMailbox()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("08:44:59");tick();assertEquals(0,beforeEvents().size());at("08:45:00");tick();tick();assertEquals(1,beforeEvents().size());assertEquals(1,inbox.size(),"reservation reminder must arrive in observable SMTP inbox");String mail=inbox.getFirst();assertTrue(mail.contains("A01"));assertTrue(mail.contains(room.toString())||mail.contains(get(user,id).path("roomName").asText()));assertTrue(mail.contains("09:00"));assertEquals("student@example.test",beforeEvents().getFirst().get("emailAddress"));}
 @Test void m105CancelAndRetry()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("08:44:00");status(200,cancel(user,id,UUID.randomUUID()));at("08:45:00");tick();assertEquals(0,beforeEvents().size());String second=reserve(other,seat2,"10:00","11:00");at("09:45:00");loseNotificationAck=true;tick();loseNotificationAck=false;at("09:45:31");tick();tick();assertEquals(1,beforeEvents().size());assertEquals(second,beforeEvents().getFirst().get("sourceId"));}
 @Test void m105LateCreationAndRecovery()throws Exception {at("08:50:00");reserve(user,seat,"09:00","10:00");tick();assertEquals(1,beforeEvents().size());at("09:20:00");tick();assertEquals(1,beforeEvents().size());assertEquals(1,inbox.stream().filter(s->s.contains("Booking reminder")).count());}
 @Test void m105VerifiedEmailOnly()throws Exception {reserve(other,seat,"09:00","10:00");at("08:45:00");unknownMail=true;tick();assertEquals(1,beforeEvents().size());assertNull(beforeEvents().getFirst().get("emailAddress"));assertEquals(0,inbox.size());}
 @Test void m107NoShowDeadline()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("09:09:59");tick();assertEquals(0,violations(id));at("09:10:00");tick();assertEquals("RESERVED",get(user,id).path("status").asText());at("09:14:59");tick();assertEquals(0,violations(id));at("09:15:00");tick();assertEquals("NO_SHOW",get(user,id).path("status").asText());assertEquals(1,violations(id));assertFalse(tree(in("GET","/internal/resources/sources/M1/"+id,null)).path("active").asBoolean());assertTrue(logical.values().stream().anyMatch(e->"预约已取消".equals(e.get("title"))));}
 @Test void m107PendingTimeoutLateAck()throws Exception {loseAllocateAck=true;var r=post(user,booking(seat,"09:00","10:00"));status(202,r);String id=tree(r).path("id").asText();at("09:15:00");tick();loseAllocateAck=false;at("09:15:31");tick();assertEquals("CANCELLED",get(user,id).path("status").asText());assertEquals("SYSTEM_TIMEOUT",get(user,id).path("cancelReason").asText());assertEquals(0,violations(id));var source=in("GET","/internal/resources/sources/M1/"+id,null);status(200,source);assertFalse(tree(source).path("active").asBoolean());assertTrue(tree(source).path("sourceVersion").asInt()>1);}
 @Test void m107Restart()throws Exception {String id=reserve(user,seat,"09:00","10:00");at("09:20:00");tick();tick();assertEquals(1,violations(id));assertEquals(1,logical.values().stream().filter(e->"预约已取消".equals(e.get("title"))).count());assertEquals(0,logical.values().stream().filter(e->"签到提醒".equals(e.get("title"))).count());}
 Map<String,Object> maintenance(UUID work,int version,boolean active)throws Exception {var r=in("PUT","/internal/resources/restrictions/"+work,map("operationId",UUID.randomUUID(),"sourceVersion",version,"roomId",room,"active",active));status(201,r);return map("operationId",UUID.randomUUID(),"sourceId",work,"sourceVersion",version,"roomId",room,"active",active,"effectiveAt",tree(r).path("effectiveAt").asText());}
 @Test void m107Maintenance()throws Exception {String a=reserve(user,seat,"09:00","10:00"),b=reserve(other,seat2,"09:00","10:00");String future=reserve(user,seat,"10:00","11:00");at("09:01:00");status(200,check(other,b,code(room)));var event=maintenance(UUID.randomUUID(),1,true);status(200,in("POST","/internal/reservations/maintenance-events",event));tick();for(String id:List.of(a,b,future)){assertEquals("SYSTEM_MAINTENANCE",get(id.equals(b)?other:user,id).path("cancelReason").asText());assertEquals(0,violations(id));}status(200,in("POST","/internal/reservations/maintenance-events",event));event.put("active",false);status(409,in("POST","/internal/reservations/maintenance-events",event));}
 @Test void m107MaintenanceOrder()throws Exception {String old=reserve(user,seat,"09:00","10:00");UUID work=UUID.randomUUID();var active=maintenance(work,1,true);status(200,in("POST","/internal/reservations/maintenance-events",active));var restore=maintenance(work,2,false);status(200,in("POST","/internal/reservations/maintenance-events",restore));String newer=reserve(other,seat2,"09:00","10:00");active.put("operationId",UUID.randomUUID());var replay=in("POST","/internal/reservations/maintenance-events",active);status(200,replay);assertTrue(tree(replay).path("ignored").asBoolean());assertEquals("RESERVED",get(other,newer).path("status").asText());assertEquals("SYSTEM_MAINTENANCE",get(user,old).path("cancelReason").asText());}
 @Test void m107Statistics()throws Exception {String a=reserve(user,seat,"09:00","10:00"),b=reserve(other,seat2,"09:00","10:00");at("09:00:00");status(200,check(user,a,code(room)));at("09:15:00");tick();String path="/internal/reservations/statistics?from="+enc(time("09:00"))+"&to="+enc(time("11:00"))+"&asOf="+enc(time("09:15"))+"&roomId="+room;var r=in("GET",path,null);status(200,r);assertEquals(1,tree(r).path("checkedInCount").asInt());assertEquals(1,tree(r).path("noShowCount").asInt());assertEquals(room.toString(),tree(r).path("roomId").asText());assertFalse(tree(r).path("algorithm").asText().isBlank());status(401,call(user,"GET",path,null,false));}
 @Test void m108OwnAdmin()throws Exception {String a=reserve(user,seat,"09:00","10:00");reserve(other,seat2,"09:00","10:00");status(403,call(other,"GET","/api/reservations/"+a,null,false));var mine=call(user,"GET","/api/reservations",null,false);status(200,mine);assertEquals(1,tree(mine).path("items").size());status(403,call(user,"GET","/api/admin/reservations",null,false));var all=call(admin,"GET","/api/admin/reservations?roomId="+room,null,false);status(200,all);assertEquals(2,tree(all).path("items").size());assertFalse(all.body().contains("@example.test"));}
 @Test void m108Rebook()throws Exception {String id=reserve(user,seat,"09:00","10:00");status(200,cancel(user,id,UUID.randomUUID()));settings(1,15,5,15);var r=call(user,"GET","/api/reservations/"+id+"/rebook",null,false);status(200,r);assertEquals(seat.toString(),tree(r).path("seatId").asText());assertEquals(1,tree(r).path("policy").path("maxReservationHours").asInt());status(400,post(user,booking(seat,"11:00","13:00")));String fresh=reserve(user,seat,"11:00","12:00");assertNotEquals(id,fresh);}
 @Test void m108StablePagination()throws Exception {for(int h=9;h<12;h++)reserve(user,seat,String.format("%02d:00",h),String.format("%02d:00",h+1));var first=call(user,"GET","/api/reservations?limit=2",null,false);status(200,first);JsonNode n=tree(first);assertEquals(2,n.path("items").size());String cursor=n.path("nextCursor").asText();assertFalse(cursor.isBlank());reserve(user,seat,"12:00","13:00");var second=call(user,"GET","/api/reservations?limit=2&cursor="+enc(cursor),null,false);status(200,second);assertEquals(1,tree(second).path("items").size());Set<String>ids=new HashSet<>();n.path("items").forEach(v->ids.add(v.path("id").asText()));assertTrue(ids.add(tree(second).path("items").get(0).path("id").asText()));}
 @Test void m108TimezoneAndEmpty()throws Exception {var empty=call(user,"GET","/api/reservations",null,false);status(200,empty);assertEquals(0,tree(empty).path("items").size());assertEquals("Asia/Shanghai",tree(empty).path("timeZone").asText());String id=reserve(user,seat,"09:00","10:00");assertEquals(OffsetDateTime.parse(time("09:00")).toInstant(),Instant.parse(get(user,id).path("startsAt").asText()));status(400,call(user,"GET","/api/reservations?cursor=broken",null,false));}
 @AfterAll void stop()throws Exception {try{if(app!=null)app.close();}finally{clients.forEach(HttpClient::close);if(notifications!=null){notifications.stop(0);((ExecutorService)notifications.getExecutor()).shutdownNow();}if(resources!=null){resources.stop(0);((ExecutorService)resources.getExecutor()).shutdownNow();}if(smtp!=null)smtp.close();if(smtpPool!=null)smtpPool.shutdownNow();}}
}
