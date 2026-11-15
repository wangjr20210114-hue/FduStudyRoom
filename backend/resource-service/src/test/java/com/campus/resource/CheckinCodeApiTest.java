package com.campus.resource;

import com.campus.ResourceApplication;
import com.campus.common.security.*;
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
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, Cookie security and PostgreSQL. Identity directory is an explicit isolation fixture. */
class CheckinCodeApiTest {
    static final UUID ADMIN=UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID USER=UUID.fromString("10000000-0000-0000-0000-000000000002");
    static final UUID R101=UUID.fromString("20000000-0000-0000-0000-000000000101");
    static final UUID R102=UUID.fromString("20000000-0000-0000-0000-000000000102");
    static final UUID R103=UUID.fromString("20000000-0000-0000-0000-000000000103");
    static final String SERVICE="resource-isolation-fixture-service-key-32";
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static ConfigurableApplicationContext app;
    static String origin;
    static class MutableClock extends Clock {
        Instant value=Instant.parse("2030-06-03T04:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value;}
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Fixture {
        @Bean @Primary @org.springframework.beans.factory.annotation.Qualifier("resourceCheckinClock") MutableClock fixtureClock(){return new MutableClock();}
        @Bean @Primary IdentityLookup trustedFixtureIdentity(){return claims -> new IdentityPrincipal(claims.userId(),"fixture","Fixture",
            claims.userId().equals(ADMIN)?"SYSTEM_ADMIN":"USER",claims.userId().equals(ADMIN)?Set.of("resource:rooms:read","resource:checkin-codes:read"):Set.of("resource:rooms:read"),1,false);}
    }
    static void start(){
        app=new SpringApplicationBuilder(ResourceApplication.class,Fixture.class).run(
            "--server.address=127.0.0.1","--server.port=0","--campus.auth.mode=password",
            "--campus.auth.signing-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
            "--campus.auth.service-key="+SERVICE,"--campus.auth.secure-cookies=true");
        origin="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
    }
    @BeforeAll static void beforeAll(){start();}
    @AfterAll static void afterAll(){if(app!=null)app.close();}
    @BeforeEach void arrange(){
        app.getBean(MutableClock.class).value=Instant.parse("2030-06-03T04:00:00Z");
        var jdbc=app.getBean(JdbcTemplate.class);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('resource.rooms') IS NOT NULL",Boolean.class))){
            jdbc.update("DELETE FROM resource.rooms");
            insert(R101,"R101",true);insert(R102,"R102",true);insert(R103,"R103",false);
        }
    }
    void insert(UUID id,String code,boolean enabled){app.getBean(JdbcTemplate.class).update("INSERT INTO resource.rooms(id,code,name,campus,building,capacity,open_time,close_time,enabled) VALUES(?,?,?,'Handan','Teaching',40,'08:00','22:00',?)",id,code,code,enabled);}
    HttpResponse<String> get(String path,UUID user)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(10)).GET();
        if(user!=null)b.header("Cookie","__Host-FDU_SESSION="+app.getBean(TokenService.class).issue(user,1));
        return HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> verify(UUID room,String date,String code,String key,UUID user)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(origin+"/internal/resources/checkin-codes/verify")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json");
        if(key!=null)b.header("X-Service-Key",key);
        if(user!=null)b.header("Cookie","__Host-FDU_SESSION="+app.getBean(TokenService.class).issue(user,1));
        return HTTP.send(b.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("roomId",room,"date",date,"code",code)))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode display(UUID room)throws Exception{var r=get("/api/resources/rooms/"+room+"/checkin-code",ADMIN);assertEquals(200,r.statusCode(),"Administrator can display current check-in code: "+r.body());return JSON.readTree(r.body());}
    boolean valid(UUID room,String date,String code)throws Exception{var r=verify(room,date,code,SERVICE,null);assertEquals(200,r.statusCode(),"Trusted verification API must be callable without user Cookie/CSRF");return JSON.readTree(r.body()).path("valid").asBoolean();}
    @Test void displaysCurrentRoomAndDate()throws Exception{
        var n=display(R101);assertEquals(R101.toString(),n.path("roomId").asText());assertEquals("R101",n.path("roomName").asText());assertEquals("2030-06-03",n.path("date").asText());assertTrue(n.path("code").asText().matches("[0-9]{6}"));assertTrue(valid(R101,"2030-06-03",n.path("code").asText()));
        var db=app.getBean(JdbcTemplate.class).queryForMap("SELECT checkin_code,checkin_code_date FROM resource.rooms WHERE id=?",R101);assertEquals(n.path("code").asText(),db.get("checkin_code"));assertEquals("2030-06-03",db.get("checkin_code_date").toString());
    }
    @Test void rejectsOtherRoomAndWrongCode()throws Exception{
        String code=display(R101).path("code").asText();String other=display(R102).path("code").asText();assertNotEquals(code,other);assertFalse(valid(R102,"2030-06-03",code));assertFalse(valid(R101,"2030-06-03",code.equals("000000")?"000001":"000000"));
    }
    @Test void ordinaryCannotReadDisplay()throws Exception{assertEquals(403,get("/api/resources/rooms/"+R101+"/checkin-code",USER).statusCode());}
    @Test void anonymousCannotReadDisplay()throws Exception{assertEquals(401,get("/api/resources/rooms/"+R101+"/checkin-code",null).statusCode());}
    @Test void expiresAtShanghaiMidnight()throws Exception{
        app.getBean(MutableClock.class).value=Instant.parse("2030-06-03T15:59:59Z");String old=display(R101).path("code").asText();assertTrue(valid(R101,"2030-06-03",old));
        app.getBean(MutableClock.class).value=Instant.parse("2030-06-03T16:00:00Z");assertFalse(valid(R101,"2030-06-03",old));assertFalse(valid(R101,"2030-06-04",old));var next=display(R101);assertEquals("2030-06-04",next.path("date").asText());assertNotEquals(old,next.path("code").asText());assertTrue(valid(R101,"2030-06-04",next.path("code").asText()));
    }
    @Test void sameDayConcurrentReadsAreStable()throws Exception{
        display(R101);var executor=Executors.newFixedThreadPool(6);
        try{var tasks=new ArrayList<Callable<String>>();for(int i=0;i<18;i++)tasks.add(()->display(R101).path("code").asText());var values=new HashSet<String>();for(var f:executor.invokeAll(tasks))values.add(f.get());assertEquals(1,values.size());assertEquals(values.iterator().next(),app.getBean(JdbcTemplate.class).queryForObject("SELECT checkin_code FROM resource.rooms WHERE id=?",String.class,R101));}finally{executor.shutdownNow();}
    }
    @Test void survivesApplicationRestart()throws Exception{String code=display(R101).path("code").asText();app.close();start();assertEquals(code,display(R101).path("code").asText());}
    @Test void doesNotExposeCodeInRoomDetailsOrServiceKeyInResponses()throws Exception{
        var bytes=new ByteArrayOutputStream();var out=System.out;var err=System.err;
        try(var capture=new PrintStream(bytes,true,StandardCharsets.UTF_8)){System.setOut(capture);System.setErr(capture);var n=display(R101);assertFalse(n.toString().contains(SERVICE));var r=get("/api/resources/rooms/"+R101,USER);assertEquals(200,r.statusCode());assertFalse(r.body().contains("checkin"));assertFalse(r.body().contains(n.path("code").asText()));assertFalse(r.body().contains(SERVICE));}finally{System.setOut(out);System.setErr(err);}
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains(SERVICE));
    }
    @Test void rejectsMissingOrIncorrectServiceKey()throws Exception{assertEquals(401,verify(R101,"2030-06-03","123456",null,null).statusCode());assertEquals(401,verify(R101,"2030-06-03","123456","wrong",null).statusCode());}
    @Test void ordinaryCannotCallInternalVerification()throws Exception{assertEquals(401,verify(R101,"2030-06-03","123456",null,USER).statusCode());}
    @Test void rejectsUnknownAndDisabledRooms()throws Exception{var missing=get("/api/resources/rooms/"+UUID.randomUUID()+"/checkin-code",ADMIN);assertEquals(404,missing.statusCode());assertTrue(missing.body().contains("ROOM_NOT_FOUND"));assertEquals(409,get("/api/resources/rooms/"+R103+"/checkin-code",ADMIN).statusCode());}
    @Test void databaseEnforcesDocumentedConstraints(){
        var jdbc=app.getBean(JdbcTemplate.class);assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('resource.rooms') IS NOT NULL",Boolean.class)),"Resource room master data must be persisted");
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE resource.rooms SET checkin_code='123456',checkin_code_date=NULL WHERE id=?",R101));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE resource.rooms SET checkin_code='bad',checkin_code_date='2030-06-03' WHERE id=?",R101));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE resource.rooms SET capacity=0 WHERE id=?",R101));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE resource.rooms SET code='R102' WHERE id=?",R101));
    }
}
