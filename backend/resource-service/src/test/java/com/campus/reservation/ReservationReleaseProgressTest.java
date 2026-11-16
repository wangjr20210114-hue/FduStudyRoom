package com.campus.reservation;
import com.campus.common.security.*;
import java.lang.reflect.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import tools.jackson.databind.JsonNode;
import static com.campus.reservation.ReservationApiTest.map;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/PG via the existing M03 fixture; only this fresh QA database is populated. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationReleaseProgressTest {
 final ReservationApiTest f=new ReservationApiTest();
 @BeforeAll void start()throws Exception{f.start();}
 @BeforeEach void arrange()throws Exception{f.arrange();}
 @AfterAll void stop()throws Exception{f.stop();}
 UUID release(String id,int version){return UUID.nameUUIDFromBytes(("M03:"+id+":RELEASE:"+version).getBytes(StandardCharsets.UTF_8));}
 JsonNode progress(JsonNode row){assertTrue(row.has("releaseProgress"),"release progress must expose actual current-version task");var p=row.path("releaseProgress");Set<String>keys=new HashSet<>();p.properties().forEach(e->keys.add(e.getKey()));assertEquals(Set.of("status","attempts","nextRetryAt","errorCode"),keys);return p;}
 String pending()throws Exception{String id=f.reserve(f.user,f.seat,"09:00","10:00");f.failRelease=true;var response=f.cancel(f.user,id,UUID.randomUUID());f.status(202,response);return id;}
 @Test void failureRetryAndCompletedReceiptAreRealAndReadsDoNotDispatch()throws Exception{
  String id=f.reserve(f.user,f.seat,"09:00","10:00");f.failRelease=true;UUID op=UUID.randomUUID();var cancelled=f.cancel(f.user,id,op);f.status(202,cancelled);var p=progress(f.tree(cancelled));assertEquals("PENDING",p.path("status").asText());assertEquals(1,p.path("attempts").asInt());assertEquals("2030-06-03T00:00:30Z",p.path("nextRetryAt").asText());assertEquals("DEPENDENCY_HTTP_503",p.path("errorCode").asText());
  var before=f.db.queryForList("SELECT id,status,attempts,next_retry_at,last_error,claim_id,lease_until FROM m1.reservation_outbox ORDER BY id");for(int i=0;i<3;i++){assertEquals(p,progress(f.get(f.user,id)));f.status(200,f.call(f.user,"GET","/api/reservations",null,false));}assertEquals(before,f.db.queryForList("SELECT id,status,attempts,next_retry_at,last_error,claim_id,lease_until FROM m1.reservation_outbox ORDER BY id"),"GET must not claim, schedule, write or dispatch any task");
  var same=f.cancel(f.user,id,op);f.status(202,same);assertEquals(1,progress(f.tree(same)).path("attempts").asInt(),"Command retry before nextRetryAt must not force an early attempt");f.failRelease=false;f.at("08:00:31");f.tick();var done=f.get(f.user,id);assertEquals("RELEASED",done.path("resourceState").asText());p=progress(done);assertEquals("DONE",p.path("status").asText());assertEquals(2,p.path("attempts").asInt());assertTrue(p.path("nextRetryAt").isNull());assertTrue(p.path("errorCode").isNull());
 }
 @Test void noCurrentTaskOmitsProgressAndOlderDoneNeverMasqueradesAsCurrent()throws Exception{
  String id=f.reserve(f.user,f.seat,"09:00","10:00");assertFalse(f.get(f.user,id).has("releaseProgress"));f.failRelease=true;f.status(202,f.cancel(f.user,id,UUID.randomUUID()));f.db.update("DELETE FROM m1.reservation_outbox WHERE reservation_id=?::uuid AND kind='RELEASE'",id);f.db.update("INSERT INTO m1.reservation_outbox(id,reservation_id,kind,payload,status,attempts,next_retry_at,created_at) VALUES(?,?::uuid,'RELEASE','{}','DONE',9,?,?)",release(id,1),id,Timestamp.from(ReservationApiTest.CLOCK.instant()),Timestamp.from(ReservationApiTest.CLOCK.instant()));
  var row=f.get(f.user,id);assertEquals(2,row.path("resourceVersion").asInt());assertEquals("RELEASE_PENDING",row.path("resourceState").asText());assertFalse(row.has("releaseProgress"),"An older DONE task is not current release success");var list=f.call(f.admin,"GET","/api/admin/reservations?roomId="+f.room,null,false);f.status(200,list);assertFalse(f.tree(list).path("items").get(0).has("releaseProgress"));
 }
 @Test void onlyWhitelistedCodesEscapeAndInFlightOrTerminalTimesAreNotRetryPromises()throws Exception{
  String id=pending();UUID event=release(id,2);for(String secret:List.of("https://hidden.invalid/?key=secret","PRIVATE_KEY_ABCD1234")){f.db.update("UPDATE m1.reservation_outbox SET last_error=? WHERE id=?",secret,event);var row=f.get(f.user,id);var p=progress(row);assertEquals("RELEASE_DEPENDENCY_UNAVAILABLE",p.path("errorCode").asText());assertFalse(row.toString().contains(secret),"Release metadata must not disclose arbitrary errors or credentials");}
  f.db.update("UPDATE m1.reservation_outbox SET status='CLAIMED',last_error='INVALID_RESOURCE_RELEASE' WHERE id=?",event);var p=progress(f.get(f.user,id));assertEquals("CLAIMED",p.path("status").asText());assertEquals("INVALID_RESOURCE_RELEASE",p.path("errorCode").asText());assertTrue(p.path("nextRetryAt").isNull());for(String status:List.of("DONE","CANCELLED")){f.db.update("UPDATE m1.reservation_outbox SET status=?,last_error='PRIVATE_KEY_ABCD1234' WHERE id=?",status,event);p=progress(f.get(f.user,id));assertEquals(status,p.path("status").asText());assertTrue(p.path("nextRetryAt").isNull());assertTrue(p.path("errorCode").isNull());}
 }
 @Test void ownershipPaginationAdminReadAndAdminCancellationKeepTheirExistingGrants()throws Exception{
  String own=f.reserve(f.user,f.seat,"09:00","10:00"),second=f.reserve(f.user,f.seat,"10:00","11:00"),foreign=f.reserve(f.other,f.seat2,"09:00","10:00");f.failRelease=true;f.status(202,f.cancel(f.user,own,UUID.randomUUID()));var adminCancel=f.call(f.admin,"POST","/api/reservations/"+second+"/admin-cancel",map("operationId",UUID.randomUUID(),"reason","Closed"),false);f.status(202,adminCancel);assertEquals("PENDING",progress(f.tree(adminCancel)).path("status").asText());f.status(202,f.cancel(f.other,foreign,UUID.randomUUID()));f.status(403,f.call(f.other,"GET","/api/reservations/"+own,null,false));f.status(403,f.call(f.user,"GET","/api/admin/reservations",null,false));
  var first=f.call(f.user,"GET","/api/reservations?limit=1",null,false);f.status(200,first);JsonNode a=f.tree(first);assertEquals(1,a.path("items").size());assertEquals(f.userId.toString(),a.path("items").get(0).path("userId").asText());progress(a.path("items").get(0));var next=f.call(f.user,"GET","/api/reservations?limit=1&cursor="+f.enc(a.path("nextCursor").asText()),null,false);f.status(200,next);JsonNode b=f.tree(next);assertEquals(1,b.path("items").size());assertEquals(f.userId.toString(),b.path("items").get(0).path("userId").asText());assertNotEquals(a.path("items").get(0).path("id"),b.path("items").get(0).path("id"));progress(b.path("items").get(0));
  var all=f.call(f.admin,"GET","/api/admin/reservations?roomId="+f.room,null,false);f.status(200,all);assertEquals(3,f.tree(all).path("items").size());for(var row:f.tree(all).path("items"))progress(row);
  HttpClient stats=f.client();String statsLogin="rp-stat-"+UUID.randomUUID().toString().substring(0,8);var registered=f.call(stats,"POST","/api/auth/register",map("loginName",statsLogin,"password",ReservationApiTest.PASS),false);f.status(201,registered);UUID statsId=UUID.fromString(f.tree(registered).path("id").asText());var role=f.call(f.admin,"POST","/api/admin/roles",map("code","RP_STAT_"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),"name","Statistics only","permissions",List.of("statistics:read")),false);f.status(201,role);f.status(204,f.call(f.admin,"PATCH","/api/admin/users/"+statsId+"/access",map("roleId",f.tree(role).path("id").asText(),"enabled",true,"expectedAuthVersion",f.tree(registered).path("authVersion").asInt()),false));f.status(200,f.call(stats,"POST","/api/auth/login",map("loginName",statsLogin,"password",ReservationApiTest.PASS),false));f.status(403,f.call(stats,"GET","/api/admin/reservations",null,false));
 }
 @Test void visibleBatchUsesOneActualOutboxReadInsteadOfOneReadPerReservation()throws Exception{
  f.failRelease=true;for(int h=9;h<12;h++){String id=f.reserve(f.user,f.seat,String.format("%02d:00",h),String.format("%02d:00",h+1));f.status(202,f.cancel(f.user,id,UUID.randomUUID()));}
  AtomicInteger reads=new AtomicInteger();DataSource real=f.app.getBean(DataSource.class);DataSource counted=new DelegatingDataSource(real){@Override public Connection getConnection()throws SQLException{Connection connection=super.getConnection();return(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{if(method.getName().equals("prepareStatement")&&args!=null&&args.length>0&&args[0]instanceof String sql&&sql.contains("m1.reservation_outbox"))reads.incrementAndGet();try{return method.invoke(connection,args);}catch(InvocationTargetException e){throw e.getCause();}});}};
  var service=new ReservationService(counted,ReservationApiTest.CLOCK,f.app.getBean(AuthSettings.class),f.app.getEnvironment(),"http://127.0.0.1:"+f.notifications.getAddress().getPort(),"http://127.0.0.1:"+f.resources.getAddress().getPort());try{var result=service.history(new IdentityPrincipal(f.userId,"owner","Owner","USER",Set.of("reservation:read"),1,false),false,100,null,null,null,null,null);assertEquals(1,reads.get(),"Visible batch release progress requires exactly one actual outbox read");var items=(List<?>)result.get("items");assertEquals(3,items.size());for(var item:items)assertTrue(((Map<?,?>)item).containsKey("releaseProgress"));}finally{service.close();}
 }
}
