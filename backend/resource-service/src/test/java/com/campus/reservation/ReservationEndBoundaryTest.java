package com.campus.reservation;
import java.net.URI;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationEndBoundaryTest {
 final ReservationApiTest h=new ReservationApiTest();
 @BeforeAll void start()throws Exception{h.start();}
 @BeforeEach void arrange()throws Exception{h.arrange();}
 @AfterAll void stop()throws Exception{h.stop();}
 @Test void adminCannotCancelEndedBeforeSchedulerSettles()throws Exception{
  String id=h.reserve(h.user,h.seat,"09:00","10:00");h.at("10:00:00");h.status(409,h.call(h.admin,"POST","/api/reservations/"+id+"/admin-cancel",ReservationApiTest.map("operationId",UUID.randomUUID(),"reason","Closed"),false));
 }
 @Test void endedReservationCannotCheckInAndPenaltyWaitsForSnapshot()throws Exception{
  h.settings(4,15,80,90);String id=h.reserve(h.user,h.seat,"09:00","10:00");h.at("10:00:00");h.status(409,h.check(h.user,id,h.code(h.room)));h.tick();assertEquals(0,h.violations(id));assertEquals(Instant.parse("2030-06-03T02:30:00Z"),Instant.parse(h.get(h.user,id).path("checkinDeadlineAt").asText()));h.at("10:30:00");h.tick();assertEquals(1,h.violations(id));assertEquals("NO_SHOW",h.get(h.user,id).path("status").asText());
 }
 @Test void endedReservationDoesNotSendAfterReminder()throws Exception{
  h.settings(4,15,80,90);String id=h.reserve(h.user,h.seat,"09:00","10:00");h.at("10:20:00");h.tick();assertEquals(0,h.logical.values().stream().filter(e->"签到提醒".equals(e.get("title"))).count(),"ended reservations must not emit obsolete reminders");assertEquals(0,h.violations(id));
 }
 @Test void legacyEndedReservationDoesNotSendAfterReminder()throws Exception{
  UUID id=UUID.randomUUID();h.db.update("INSERT INTO m1.seat_reservations(id,user_id,seat_id,starts_at,ends_at,checkin_deadline_at,policy_snapshot) VALUES(?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,'{}'::jsonb)",id,h.userId,h.seat,h.time("09:00"),h.time("09:05"),h.time("09:15"));h.at("09:10:00");new ReminderDispatcher(h.app.getBean(DataSource.class),ReservationApiTest.CLOCK,URI.create("http://127.0.0.1:"+h.notifications.getAddress().getPort()),ReservationApiTest.KEY).tick();assertEquals(0,h.logical.size(),"ended reservations must not emit obsolete reminders");assertEquals("RESERVED",h.get(h.user,id.toString()).path("status").asText());
 }
}
