package com.campus.reservation;

import com.sun.net.httpserver.HttpServer;
import javax.sql.DataSource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real isolated PostgreSQL and loopback HTTP contract fixture; not c+f integration. */
class OverdueReminderIntegrationTest {
 private final List<UUID> owned=new ArrayList<>();
 private DataSource source; private JdbcTemplate jdbc; private HttpServer server;
 private final MutableClock clock=new MutableClock(Instant.parse("2026-11-15T09:00:00Z"));
 private final List<Map<String,Object>> requests=new CopyOnWriteArrayList<>();
 private final Set<String> logicalEvents=ConcurrentHashMap.newKeySet();
 private final AtomicInteger sends=new AtomicInteger(), cancels=new AtomicInteger();
 private volatile int failureCode=0, cancelCode=200; private volatile boolean loseAcknowledgement=false;
 private static final String KEY="contract-fixture-service-key-32-characters";
 @BeforeEach void setup() throws Exception {
  source=new DriverManagerDataSource(System.getenv("CAMPUS_DB_URL"),System.getenv("CAMPUS_DB_USER"),System.getenv("CAMPUS_DB_PASSWORD"));
  jdbc=new JdbcTemplate(source);
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/notifications/events", exchange->{
   try {
    assertEquals("POST",exchange.getRequestMethod());assertEquals(KEY,exchange.getRequestHeaders().getFirst("X-Service-Key"));
    if(exchange.getRequestURI().getPath().endsWith("/cancel")) {
     cancels.incrementAndGet();exchange.sendResponseHeaders(cancelCode,-1);return;
    }
    sends.incrementAndGet();
    var event=JsonMapper.builder().build().readValue(exchange.getRequestBody().readAllBytes(),Map.class);
    requests.add(event);
    if(failureCode!=0){exchange.sendResponseHeaders(failureCode,-1);return;}
    boolean fresh=logicalEvents.add((String)event.get("eventId"));
    if(loseAcknowledgement){exchange.close();return;}
    byte[] response=("{\"eventId\":\""+event.get("eventId")+"\",\"notificationId\":\""+UUID.randomUUID()+"\",\"duplicate\":"+!fresh+",\"inAppStatus\":\"SENT\",\"emailStatus\":\"NOT_CONFIGURED\"}").getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(fresh?201:200,response.length);exchange.getResponseBody().write(response);
   } finally {exchange.close();}
  });server.start();
 }
 @AfterEach void cleanup(){if(server!=null)server.stop(0);for(UUID id:owned){jdbc.update("DELETE FROM m1.reminder_tasks WHERE reservation_id=?",id);jdbc.update("DELETE FROM m1.seat_reservations WHERE id=?",id);}}
 private Object scheduler() throws Exception {
  Class<?> type;
  try {type=Class.forName("com.campus.reservation.ReminderDispatcher");}
  catch(ClassNotFoundException missing){fail("M1-06 must persist and dispatch the overdue reminder",missing);return null;}
  assertNotNull(getClass().getResource("/db/modules/reservation/migration/V1__seat_reminder_tasks.sql"),"M1-06 requires durable reservation and reminder storage");
  migrations().migrate();
  return type.getConstructor(DataSource.class,Clock.class,URI.class,String.class).newInstance(source,clock,URI.create("http://127.0.0.1:"+server.getAddress().getPort()),KEY);
 }
 private Flyway migrations(){return Flyway.configure().dataSource(source).defaultSchema("reservation_meta").schemas("reservation_meta","m1").locations("classpath:db/modules/reservation/migration").cleanDisabled(true).load();}
 private void tick(Object worker)throws Exception {worker.getClass().getMethod("tick").invoke(worker);}
 private UUID reserve(String status) {
  UUID id=UUID.randomUUID(), user=UUID.randomUUID();
  jdbc.update("INSERT INTO m1.seat_reservations(id,user_id,seat_id,starts_at,ends_at,status,checked_in_at,checkin_deadline_at,cancel_reason,cancelled_at,policy_snapshot) VALUES(?,?,?,?::timestamptz,?::timestamptz,?,?::timestamptz,?::timestamptz,?,?::timestamptz,'{}'::jsonb)",id,user,UUID.randomUUID(),"2026-11-15T09:00:00Z","2026-11-15T10:00:00Z",status,status.equals("CHECKED_IN")?"2026-11-15T09:09:00Z":null,"2026-11-15T09:15:00Z",status.equals("CANCELLED")?"USER":null,status.equals("CANCELLED")?"2026-11-15T09:09:00Z":null);
  owned.add(id);return id;
 }
 private String task(UUID id,String field){return jdbc.queryForObject("SELECT "+field+"::text FROM m1.reminder_tasks WHERE reservation_id=?",String.class,id);}
 // S01#1: actual time boundary, persisted source/recipient and bilingual content.
 @Test void tenMinuteBoundarySendsOnlyOnceAndBilingualNotViolation()throws Exception {
  var worker=scheduler();UUID id=reserve("RESERVED");clock.at("2026-11-15T09:09:59Z");tick(worker);assertEquals(0,sends.get());
  assertNotNull(task(id,"event_id"));clock.at("2026-11-15T09:10:00Z");tick(worker);tick(worker);
  assertEquals(1,sends.get(),"M1-06 sends exactly one reminder at start +10 minutes");
  var e=requests.getFirst();assertEquals(id.toString(),e.get("sourceId"));assertEquals("M1",e.get("sourceModule"));
  assertEquals(jdbc.queryForObject("SELECT user_id::text FROM m1.seat_reservations WHERE id=?",String.class,id),e.get("recipientId"));
  assertEquals(Instant.parse("2026-11-15T09:10:00Z"),Instant.parse((String)e.get("scheduledAt")));
  assertEquals("签到提醒",e.get("title"));assertEquals("Check-in reminder",e.get("titleEn"));
  assertTrue(((String)e.get("body")).contains("尚未签到"));assertFalse(((String)e.get("body")).contains("违约"));
  assertTrue(((String)e.get("bodyEn")).contains("not checked in"));assertFalse(e.containsKey("emailAddress"));assertEquals("SENT",task(id,"status"));
 }
 // S02#1
 @Test void checkedInAndCancelledReservationsRemainUnchanged()throws Exception {
  var worker=scheduler();UUID a=reserve("CHECKED_IN"),b=reserve("CANCELLED");clock.at("2026-11-15T09:10:00Z");tick(worker);tick(worker);
  assertEquals(0,sends.get());assertEquals("CHECKED_IN",jdbc.queryForObject("SELECT status FROM m1.seat_reservations WHERE id=?",String.class,a));assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM m1.seat_reservations WHERE id=?",String.class,b));
 }
 // S03#1: fixture accepts event then loses HTTP ack, sender recreates from DB.
 @Test void restartAfterLostAcknowledgementReusesDurableEvent()throws Exception {
  var worker=scheduler();UUID id=reserve("RESERVED");loseAcknowledgement=true;clock.at("2026-11-15T09:10:00Z");tick(worker);
  assertEquals("PENDING",task(id,"status"));String stable=task(id,"event_id");assertEquals(1,logicalEvents.size());
  loseAcknowledgement=false;clock.at("2026-11-15T09:10:31Z");tick(scheduler());
  assertEquals("SENT",task(id,"status"));assertEquals(1,logicalEvents.size());assertTrue(requests.size()>=2);assertEquals(stable,requests.getFirst().get("eventId"));
  for(var e:requests)assertEquals(requests.getFirst(),e,"Retry payload must be byte-semantically stable");
 }
 // S03#2
 @Test void networkFailureKeepsDurableTaskAndRetry()throws Exception {
  var worker=scheduler();UUID id=reserve("RESERVED");failureCode=503;clock.at("2026-11-15T09:10:00Z");tick(worker);
  assertEquals("PENDING",task(id,"status"));assertEquals("HTTP_503",task(id,"last_error_code"));String stable=task(id,"event_id");
  tick(worker);assertEquals(1,sends.get(),"Backoff prevents immediate retry");failureCode=0;clock.at("2026-11-15T09:10:31Z");tick(scheduler());
  assertEquals("SENT",task(id,"status"));assertEquals(stable,requests.getLast().get("eventId"));assertEquals(1,logicalEvents.size());
 }
 // S03#3
 @Test void concurrentSchedulersOnlyDeliverOnce()throws Exception {
  var a=scheduler();var b=scheduler();reserve("RESERVED");clock.at("2026-11-15T09:10:00Z");var pool=Executors.newFixedThreadPool(2);
  try {var x=pool.submit(()->{try{tick(a);}catch(Exception e){throw new RuntimeException(e);}});var y=pool.submit(()->{try{tick(b);}catch(Exception e){throw new RuntimeException(e);}});x.get(15,TimeUnit.SECONDS);y.get(15,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertEquals(1,sends.get());assertEquals(1,logicalEvents.size());
 }
 // S01#2: no obsolete reminder on late recovery (S032.SC02 cross-reference).
 @Test void deadlineSuppressesStaleReminder()throws Exception {
  var worker=scheduler();UUID id=reserve("RESERVED");clock.at("2026-11-15T09:15:00Z");tick(worker);assertEquals(0,sends.get());assertEquals("CANCELLED",task(id,"status"));assertEquals("RESERVED",jdbc.queryForObject("SELECT status FROM m1.seat_reservations WHERE id=?",String.class,id));
 }
 // S02#2: pending unknown receipt cancellation retains 404 retry per frozen contract.
 @Test void pendingRetryCancelledAfterCheckin()throws Exception {
  var worker=scheduler();UUID id=reserve("RESERVED");loseAcknowledgement=true;clock.at("2026-11-15T09:10:00Z");tick(worker);int before=sends.get();
  jdbc.update("UPDATE m1.seat_reservations SET status='CHECKED_IN',checked_in_at=now() WHERE id=?",id);loseAcknowledgement=false;cancelCode=404;clock.at("2026-11-15T09:10:31Z");tick(scheduler());
  assertEquals(before,sends.get());assertEquals(1,cancels.get());assertEquals("CANCEL_PENDING",task(id,"status"));
  cancelCode=200;clock.at("2026-11-15T09:11:02Z");tick(scheduler());assertEquals("CANCELLED",task(id,"status"));assertEquals(2,cancels.get());
 }
 // S01#3: actual PostgreSQL constraints and non-destructive replay.
 @Test void databaseConstraintsAndMigrationReplay()throws Exception {
  scheduler();UUID id=reserve("RESERVED");migrations().migrate();assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM m1.seat_reservations WHERE id=?",Integer.class,id));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE m1.seat_reservations SET ends_at=starts_at WHERE id=?",id));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE m1.seat_reservations SET status='CHECKED_IN',checked_in_at=NULL WHERE id=?",id));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE m1.seat_reservations SET status='CANCELLED',cancel_reason=NULL WHERE id=?",id));
 }
 static final class MutableClock extends Clock {
  private volatile Instant now;MutableClock(Instant i){now=i;}void at(String value){now=Instant.parse(value);}
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
 }
}
