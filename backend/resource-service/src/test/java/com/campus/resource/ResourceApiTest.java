package com.campus.resource;
import com.campus.ResourceApplication;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
@TestInstance(TestInstance.Lifecycle.PER_CLASS) class ResourceApiTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String KEY="resource-round3-fixture-service-key-32",PASS="resource-round3-fixture-password";
    static final UUID ROOM=UUID.fromString("30000000-0000-0000-0000-000000000101"),OTHER=UUID.fromString("30000000-0000-0000-0000-000000000102"),SEAT=UUID.fromString("40000000-0000-0000-0000-000000000001"),SEAT2=UUID.fromString("40000000-0000-0000-0000-000000000002"),OTHERSEAT=UUID.fromString("40000000-0000-0000-0000-000000000003");
    static class TestClock extends Clock {
        volatile Instant now=Instant.parse("2030-06-03T00:00:00Z");
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
    @TestConfiguration(proxyBeanMethods=false) static class Fixture {
        @Bean @Primary @org.springframework.beans.factory.annotation.Qualifier("resourceCheckinClock") TestClock fixtureClock(){
            return new TestClock();
        }
    }
    ConfigurableApplicationContext app;
    JdbcTemplate db;
    String base;
    HttpClient admin,user;
    UUID userId,user2Id;
    HttpServer eligibility;
    volatile boolean failEligibility;
    int poolSize=10;
    boolean eligibilityBorrowsConnection;
    @BeforeAll void start()throws Exception{
        eligibility=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        eligibility.createContext("/internal/identity/users/",e->{
            try{
                if(!KEY.equals(e.getRequestHeaders().getFirst("X-Service-Key"))){
                    e.sendResponseHeaders(401,-1);return;
                }
                if(failEligibility){
                    e.sendResponseHeaders(503,-1);return;
                }
                if(eligibilityBorrowsConnection&&db!=null)db.queryForObject("SELECT 1",Integer.class);String id=e.getRequestURI().getPath().split("/")[4];byte[] b=JSON.writeValueAsBytes(map("userId",id,"enabled",true,"departmentCode",id.equals(String.valueOf(userId))?"CS":null,"canApplyRoom",true,"authVersion",1));e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);
            }
            finally{
                e.close();
            }
        });
        eligibility.start();
        String name="res-admin-"+UUID.randomUUID().toString().substring(0,7);
        app=new SpringApplicationBuilder(ResourceApplication.class,Fixture.class).run("--spring.datasource.hikari.maximum-pool-size="+poolSize,"--spring.datasource.hikari.connection-timeout=1000","--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=0","--campus.auth.secure-cookies=false","--campus.auth.signing-key="+Base64.getEncoder().encodeToString(new byte[32]),"--campus.auth.service-key="+KEY,"--campus.bootstrap-admin.login-name="+name,"--campus.bootstrap-admin.password="+PASS,"--campus.resource.identity-url=http://127.0.0.1:"+eligibility.getAddress().getPort());
        db=app.getBean(JdbcTemplate.class);
        base="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port");
        admin=client();
        assertEquals(200,call(admin,"POST","/api/auth/login",map("loginName",name,"password",PASS),false).statusCode());
        String uname="res-user-"+UUID.randomUUID().toString().substring(0,7);
        user=client();
        var u=call(user,"POST","/api/auth/register",map("loginName",uname,"password",PASS),false);
        assertEquals(201,u.statusCode());
        userId=UUID.fromString(tree(u).path("id").asText());
        HttpClient u2=client();
        var second=call(u2,"POST","/api/auth/register",map("loginName","res-user-"+UUID.randomUUID().toString().substring(0,7),"password",PASS),false);
        assertEquals(201,second.statusCode());
        user2Id=UUID.fromString(tree(second).path("id").asText());
        var role=call(admin,"POST","/api/admin/roles",map("code","RESOURCE_"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(java.util.Locale.ROOT),"name","Resource reader fixture","permissions",List.of("resource:rooms:read")),false);
        assertEquals(201,role.statusCode());
        assertEquals(204,call(admin,"PATCH","/api/admin/users/"+userId+"/access",map("roleId",tree(role).path("id").asText(),"enabled",true,"expectedAuthVersion",1),false).statusCode());
        user=client();
        assertEquals(200,call(user,"POST","/api/auth/login",map("loginName",uname,"password",PASS),false).statusCode());
    }
    @AfterAll void stop(){
        if(app!=null){
            clean();
            app.close();
        }
        if(eligibility!=null)eligibility.stop(0);
    }
    boolean exists(String table){
        return Boolean.TRUE.equals(db.queryForObject("SELECT to_regclass(?) IS NOT NULL",Boolean.class,table));
    }
    void clean(){
        for(String t:List.of("resource.resource_operations","resource.seat_allocations","resource.room_restrictions","resource.seats","resource.rooms"))if(exists(t))db.update("DELETE FROM "+t);
    }
    @BeforeEach void arrange(){
        failEligibility=false;
        clock("00:00");
        clean();
        db.update("INSERT INTO resource.rooms(id,code,name,campus,building,capacity,open_time,close_time,booking_enabled) VALUES(?, 'R101','R101','Handan','A',4,'08:00','22:00',true),(?,'R102','R102','Handan','A',4,'08:00','22:00',true)",ROOM,OTHER);
        if(exists("resource.seats"))db.update("INSERT INTO resource.seats(id,room_id,code,socket_type) VALUES(?,?,'A01','FIXED'),(?,?,'A02','NONE'),(?,?,'B01','NONE')",SEAT,ROOM,SEAT2,ROOM,OTHERSEAT,OTHER);
    }
    void clock(String hhmm){
        app.getBean(TestClock.class).now=Instant.parse("2030-06-03T"+hhmm+":00Z");
    }
    static Map<String,Object> map(Object...a){
        Map<String,Object>m=new LinkedHashMap<>();
        for(int i=0;i<a.length;i+=2)m.put((String)a[i],a[i+1]);
        return m;
    }
    static HttpClient client(){
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
    }
    HttpResponse<String> call(HttpClient c,String verb,String path,Object body,boolean internal)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
        if(internal)b.header("X-Service-Key",KEY);
        else if(!verb.equals("GET")){
            var token=c.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            var n=tree(token);
            b.header(n.path("headerName").asText(),n.path("token").asText());
        }
        return c.send(b.method(verb,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> in(String verb,String path,Object body)throws Exception{
        return call(client(),verb,path,body,true);
    }
    JsonNode tree(HttpResponse<String>r){
        return JSON.readTree(r.body());
    }
    void status(int n,HttpResponse<String>r){
        assertEquals(n,r.statusCode(),"resource contract response: "+r.body());
    }
    Map<String,Object> room(String code){
        return map("code",code,"name",code,"campus","Handan","building","A","capacity",4,"openTime","08:00","closeTime","22:00","departmentCode",null,"bookingEnabled",true,"enabled",true,"expectedVersion",1);
    }
    Map<String,Object> alloc(String module,UUID source,UUID room,UUID seat,UUID user,String start,String end){
        return map("operationId",UUID.randomUUID(),"sourceModule",module,"sourceId",source,"sourceVersion",1,"roomId",room,"seatId",seat,"userId",user,"startsAt","2030-06-03T"+start+":00Z","endsAt","2030-06-03T"+end+":00Z");
    }
    HttpResponse<String> allocate(Map<String,Object>b)throws Exception{
        return in("POST","/internal/resources/allocations",b);
    }
    HttpResponse<String> restriction(UUID id,int version,boolean active)throws Exception{
        return in("PUT","/internal/resources/restrictions/"+id,map("operationId",UUID.randomUUID(),"sourceVersion",version,"roomId",ROOM,"active",active));
    }
    long count(String table){
        return exists(table)?db.queryForObject("SELECT count(*) FROM "+table,Long.class):0;
    }
    @Test void r01CreateAndRead()throws Exception{
        var c=call(admin,"POST","/api/resources/rooms",room("NEW"),false);
        status(201,c);
        String id=tree(c).path("id").asText();
        var d=call(admin,"GET","/api/resources/rooms/"+id,null,false);
        status(200,d);
        assertEquals("Handan",tree(d).path("campus").asText());
        assertTrue(call(admin,"GET","/api/resources/rooms",null,false).body().contains(id));
    }
    @Test void r01DenyOrdinary()throws Exception{
        status(403,call(user,"POST","/api/resources/rooms",room("DENIED"),false));
        assertEquals(2,count("resource.rooms"));
    }
    @Test void r01RejectInvalidAndStale()throws Exception{
        var b=room("R101");
        b.put("capacity",0);
        status(400,call(admin,"PUT","/api/resources/rooms/"+ROOM,b,false));
        b.put("capacity",4);
        b.put("expectedVersion",0);
        status(409,call(admin,"PUT","/api/resources/rooms/"+ROOM,b,false));
        assertEquals(4,db.queryForObject("SELECT capacity FROM resource.rooms WHERE id=?",Integer.class,ROOM));
    }
    @Test void r01DisablePreservesHistory()throws Exception{
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00")));
        var b=room("R101");
        b.put("enabled",false);
        status(200,call(admin,"PUT","/api/resources/rooms/"+ROOM,b,false));
        status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT2,user2Id,"03:00","04:00")));
        assertEquals(1,count("resource.seat_allocations"));
        status(200,call(admin,"GET","/api/resources/rooms/"+ROOM,null,false));
    }
    @Test void r02DuplicateAndSocketFilter()throws Exception{
        status(409,call(admin,"POST","/api/resources/rooms/"+ROOM+"/seats",map("code","A01","socketType","NONE","enabled",true),false));
        status(201,call(admin,"POST","/api/resources/rooms/"+OTHER+"/seats",map("code","A01","socketType","RAIL","enabled",true),false));
        var r=call(user,"GET","/api/resources/rooms/"+ROOM+"/seats?hasSocket=true",null,false);
        status(200,r);
        assertEquals(1,tree(r).size());
        assertEquals("A01",tree(r).get(0).path("code").asText());
    }
    @Test void r02DisablePreservesSource()throws Exception{
        UUID source=UUID.randomUUID();
        status(201,allocate(alloc("M1",source,ROOM,SEAT,userId,"01:00","02:00")));
        status(200,call(admin,"PUT","/api/resources/seats/"+SEAT,map("code","A01","socketType","FIXED","enabled",false,"expectedVersion",1),false));
        status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,user2Id,"03:00","04:00")));
        status(200,in("GET","/internal/resources/sources/M1/"+source,null));
        assertTrue(db.queryForObject("SELECT enabled FROM resource.seats WHERE id=?",Boolean.class,SEAT2));
    }
    @Test void r03OpenBoundaries()throws Exception{
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"00:00","01:00")));
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"13:00","14:00")));
        status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"14:00","15:00")));
    }
    @Test void r03OvernightAndFullDay()throws Exception{
        var b=room("R101");
        b.put("openTime","22:00");
        b.put("closeTime","06:00");
        status(200,call(admin,"PUT","/api/resources/rooms/"+ROOM,b,false));
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"15:00","17:00")));
        b.put("expectedVersion",2);
        b.put("openTime","00:00");
        b.put("closeTime","00:00");
        status(200,call(admin,"PUT","/api/resources/rooms/"+ROOM,b,false));
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT2,userId,"18:00","19:00")));
    }
    @Test void r03TrustedEligibility()throws Exception{
        db.update("UPDATE resource.rooms SET department_code='CS' WHERE id=?",OTHER);
        status(201,allocate(alloc("M1",UUID.randomUUID(),OTHER,OTHERSEAT,userId,"01:00","02:00")));
        var b=alloc("M1",UUID.randomUUID(),OTHER,OTHERSEAT,user2Id,"03:00","04:00");
        b.put("departmentCode","CS");
        status(403,allocate(b));
    }
    @Test void r03DependencyFailsClosed()throws Exception{
        failEligibility=true;
        status(503,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00")));
        assertEquals(0,count("resource.seat_allocations"));
    }
    List<Integer> race(Map<String,Object>a,Map<String,Object>b)throws Exception{
        var ex=Executors.newFixedThreadPool(2);
        var gate=new CountDownLatch(1);
        try{
            var x=ex.submit(()->{
                gate.await();return allocate(a).statusCode();
            });
            var y=ex.submit(()->{
                gate.await();return allocate(b).statusCode();
            });
            gate.countDown();
            return List.of(x.get(),y.get());
        }
        finally{
            ex.shutdownNow();
        }
    }
    @Test void r04SameSeatRace()throws Exception{
        assertEquals(Set.of(201,409),new HashSet<>(race(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00"),alloc("M1",UUID.randomUUID(),ROOM,SEAT,user2Id,"01:00","02:00"))));
        assertEquals(1,count("resource.seat_allocations"));
    }
    @Test void r04SameUserAcrossRooms()throws Exception{
        assertEquals(Set.of(201,409),new HashSet<>(race(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00"),alloc("M1",UUID.randomUUID(),OTHER,OTHERSEAT,userId,"01:00","02:00"))));
    }
    @Test void r04AdjacentAllowed()throws Exception{
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00")));
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT2,userId,"02:00","03:00")));
    }
    @Test void r04WholeRoomAtomic()throws Exception{
        status(201,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00")));
        UUID whole=UUID.randomUUID();
        status(409,allocate(alloc("M2",whole,ROOM,null,null,"01:00","02:00")));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM resource.seat_allocations WHERE source_id=?",Integer.class,whole));
    }
    @Test void r04WholeRoomVersusPersonRace()throws Exception{
        var results=race(alloc("M2",UUID.randomUUID(),ROOM,null,null,"01:00","02:00"),alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00"));
        assertEquals(Set.of(201,409),new HashSet<>(results));
        assertTrue(count("resource.seat_allocations")==1||count("resource.seat_allocations")==2);
    }
    @Test void r04NewSeatsInheritWholeRoom()throws Exception{
        status(201,allocate(alloc("M2",UUID.randomUUID(),ROOM,null,null,"01:00","02:00")));
        var c=call(admin,"POST","/api/resources/rooms/"+ROOM+"/seats",map("code","A03","socketType","NONE","enabled",true),false);
        status(201,c);
        status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,UUID.fromString(tree(c).path("id").asText()),userId,"01:00","02:00")));
    }
    @Test void r04DisabledRejectsNoResidue()throws Exception{
        db.update("UPDATE resource.rooms SET booking_enabled=false WHERE id=?",ROOM);
        status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00")));
        assertEquals(0,count("resource.seat_allocations"));
    }
    @Test void r05WholeRoomMaintenance()throws Exception{
        status(201,restriction(UUID.randomUUID(),1,true));
        for(UUID s:List.of(SEAT,SEAT2))status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,s,userId,"01:00","02:00")));
    }
    @Test void r05ReleaseOnlyOwnRestriction()throws Exception{
        UUID w1=UUID.randomUUID(),w2=UUID.randomUUID();
        status(201,restriction(w1,1,true));
        status(201,restriction(w2,1,true));
        status(201,restriction(w1,2,false));
        status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00")));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM resource.room_restrictions WHERE active",Integer.class));
    }
    @Test void r05OldRestoreCannotWin()throws Exception{
        UUID w=UUID.randomUUID();
        status(201,restriction(w,2,true));
        status(409,restriction(w,1,false));
        status(200,restriction(w,2,true));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM resource.room_restrictions WHERE active",Integer.class));
    }
    @Test void r05NewSeatsInheritMaintenance()throws Exception{
        status(201,restriction(UUID.randomUUID(),1,true));
        var c=call(admin,"POST","/api/resources/rooms/"+ROOM+"/seats",map("code","A03","socketType","NONE","enabled",true),false);
        status(201,c);
        status(409,allocate(alloc("M1",UUID.randomUUID(),ROOM,UUID.fromString(tree(c).path("id").asText()),userId,"01:00","02:00")));
    }
    @Test void r06IdempotentAndChangedPayload()throws Exception{
        var b=alloc("M1",UUID.randomUUID(),ROOM,SEAT,userId,"01:00","02:00");
        status(201,allocate(b));
        status(200,allocate(b));
        assertEquals(1,count("resource.seat_allocations"));
        b.put("endsAt","2030-06-03T03:00:00Z");
        status(409,allocate(b));
    }
    @Test void r06TombstoneRejectsOldAllocation()throws Exception{
        UUID id=UUID.randomUUID();
        status(201,in("POST","/internal/resources/releases",map("operationId",UUID.randomUUID(),"sourceModule","M1","sourceId",id,"sourceVersion",2)));
        status(409,allocate(alloc("M1",id,ROOM,SEAT,userId,"01:00","02:00")));
        assertEquals(0,count("resource.seat_allocations"));
    }
    @Test void r06SourcesProtectedAndVersioned()throws Exception{
        UUID id=UUID.randomUUID();
        status(201,allocate(alloc("M1",id,ROOM,SEAT,userId,"01:00","02:00")));
        var r=in("GET","/internal/resources/sources/M1/"+id,null);
        status(200,r);
        assertEquals(id.toString(),tree(r).path("sourceId").asText());
        assertEquals(1,tree(r).path("sourceVersion").asInt());
        status(401,call(user,"GET","/internal/resources/sources/M1/"+id,null,false));
        assertFalse(r.body().contains(PASS));
    }
    @Test void r06HistoricalStatistics()throws Exception{
        var r=call(admin,"POST","/api/resources/rooms",room("STATS"),false);
        status(201,r);
        UUID room=UUID.fromString(tree(r).path("id").asText());
        UUID a=null;
        for(String code:List.of("S1","S2")){
            var s=call(admin,"POST","/api/resources/rooms/"+room+"/seats",map("code",code,"socketType","NONE","enabled",true),false);
            status(201,s);
            a=UUID.fromString(tree(s).path("id").asText());
        }
        UUID id=UUID.randomUUID();
        status(201,allocate(alloc("M1",id,room,a,userId,"01:00","03:00")));
        clock("02:00");
        status(201,in("POST","/internal/resources/releases",map("operationId",UUID.randomUUID(),"sourceModule","M1","sourceId",id,"sourceVersion",2)));
        clock("02:30");
        UUID w=UUID.randomUUID();
        status(201,in("PUT","/internal/resources/restrictions/"+w,map("operationId",UUID.randomUUID(),"sourceVersion",1,"roomId",room,"active",true)));
        clock("03:00");
        status(201,in("PUT","/internal/resources/restrictions/"+w,map("operationId",UUID.randomUUID(),"sourceVersion",2,"roomId",room,"active",false)));
        status(201,allocate(alloc("M2",UUID.randomUUID(),room,null,null,"03:00","04:00")));
        clock("04:00");
        var stat=in("GET","/internal/resources/statistics?from=2030-06-03T00:00:00Z&to=2030-06-03T04:00:00Z&asOf=2030-06-03T04:00:00Z&roomId="+room,null);
        status(200,stat);
        var n=tree(stat);
        assertTrue(n.path("historyComplete").asBoolean());
        assertEquals(25200,n.path("capacitySeatSeconds").asLong());
        assertEquals(10800,n.path("occupiedSeatSeconds").asLong());
        assertEquals(2,n.path("confirmedBookingCount").asInt());
        assertEquals(1,n.path("confirmedM1BookingCount").asInt());
        var missing=in("GET","/internal/resources/statistics?from=2030-06-02T23:00:00Z&to=2030-06-03T04:00:00Z&asOf=2030-06-03T04:00:00Z&roomId="+room,null);
        status(200,missing);
        assertTrue(tree(missing).path("historyComplete").asBoolean());
        assertEquals(25200,tree(missing).path("capacitySeatSeconds").asLong());
    }
}
