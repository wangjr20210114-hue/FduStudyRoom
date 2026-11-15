package com.campus.reservation;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Persistent M1-06 sender. The notification service owns logical delivery idempotency. */
public final class ReminderDispatcher {
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transactions;
 private final Clock clock;
 private final URI events;
 private final String serviceKey;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 private final JsonMapper json=JsonMapper.builder().build();
 public ReminderDispatcher(DataSource source,Clock clock,URI endpoint,String serviceKey) {
  if(!Set.of("http","https").contains(endpoint.getScheme())||endpoint.getHost()==null||endpoint.getUserInfo()!=null||endpoint.getQuery()!=null||endpoint.getFragment()!=null)
   throw new IllegalArgumentException("Invalid notification service endpoint");
  if(serviceKey==null||serviceKey.length()<32)throw new IllegalArgumentException("Internal service key requires at least 32 characters");
  this.jdbc=new JdbcTemplate(source);this.transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
  this.clock=clock;this.events=endpoint.resolve("/internal/notifications/events");this.serviceKey=serviceKey;
 }
 @Scheduled(fixedDelayString="${campus.reservation.reminder-delay-ms:5000}")
 public void tick() {
  Instant now=clock.instant();
  // Commit the event identity and full payload BEFORE attempting HTTP. A lost ACK or
  // process death cannot turn a retry into a new logical notification.
  transactions.executeWithoutResult(tx->createTasks(now));
  for(int i=0;i<100;i++)if(!Boolean.TRUE.equals(transactions.execute(tx->dispatchOne(now))))break;
 }
 private void createTasks(Instant now) {
  var rows=jdbc.queryForList("SELECT r.id,r.user_id,r.starts_at FROM m1.seat_reservations r WHERE r.room_id IS NULL AND r.status='RESERVED' AND r.checked_in_at IS NULL AND NOT EXISTS(SELECT 1 FROM m1.reminder_tasks t WHERE t.reservation_id=r.id) ORDER BY r.starts_at,r.id LIMIT 100 FOR UPDATE OF r SKIP LOCKED");
  for(var row:rows) {
   UUID event=UUID.randomUUID();UUID id=(UUID)row.get("id");
   Instant due=instant(row.get("starts_at")).plus(Duration.ofMinutes(10));
   var payload=new Event(event.toString(),row.get("user_id").toString(),"M1",id.toString(),"签到提醒","您的预约尚未签到，请及时签到。","Check-in reminder","You have not checked in for your reservation. Please check in.",due.toString());
   jdbc.update("INSERT INTO m1.reminder_tasks(event_id,reservation_id,scheduled_at,payload,next_retry_at) VALUES(?,?,?,?::jsonb,?) ON CONFLICT(reservation_id) DO NOTHING",event,id,Timestamp.from(due),json.writeValueAsString(payload),Timestamp.from(now));
  }
 }
 private boolean dispatchOne(Instant now) {
  var rows=jdbc.queryForList("SELECT t.event_id,t.reservation_id,t.payload::text AS payload,t.attempts,t.status,r.status AS reservation_status,r.checked_in_at,r.checkin_deadline_at,r.ends_at FROM m1.reminder_tasks t JOIN m1.seat_reservations r ON r.id=t.reservation_id WHERE t.status IN ('PENDING','CANCEL_PENDING') AND t.next_retry_at<=? AND (t.scheduled_at<=? OR r.status<>'RESERVED' OR r.checked_in_at IS NOT NULL) ORDER BY t.scheduled_at,t.event_id LIMIT 1 FOR UPDATE OF r,t SKIP LOCKED",Timestamp.from(now),Timestamp.from(now));
  if(rows.isEmpty())return false;
  var row=rows.getFirst();UUID event=(UUID)row.get("event_id");
  Instant deadline=instant(row.get("checkin_deadline_at"));
  boolean cancel=!"RESERVED".equals(row.get("reservation_status"))||row.get("checked_in_at")!=null||!now.isBefore(deadline)||!now.isBefore(instant(row.get("ends_at")))||"CANCEL_PENDING".equals(row.get("status"));
  int attempts=((Number)row.get("attempts")).intValue();
  if(cancel&&attempts==0){finish(event,"CANCELLED",attempts,null,now);return true;}
  URI endpoint=cancel?URI.create(events+"/"+event+"/cancel"):events;
  try {
   var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("X-Service-Key",serviceKey).header("Content-Type","application/json")
    .POST(cancel?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString((String)row.get("payload"))).build();
   int status=http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
   if((cancel&&status==200)||(!cancel&&(status==201||status==200)))finish(event,cancel?"CANCELLED":"SENT",attempts+1,null,now);
   else finish(event,cancel?"CANCEL_PENDING":"PENDING",attempts+1,"HTTP_"+status,now.plusSeconds(30));
  } catch(InterruptedException interrupted) {
   Thread.currentThread().interrupt();finish(event,cancel?"CANCEL_PENDING":"PENDING",attempts+1,"NOTIFICATION_UNAVAILABLE",now.plusSeconds(30));
  } catch(java.io.IOException unavailable) {
   finish(event,cancel?"CANCEL_PENDING":"PENDING",attempts+1,"NOTIFICATION_UNAVAILABLE",now.plusSeconds(30));
  }
  return true;
 }
 private void finish(UUID event,String status,int attempts,String error,Instant next) {
  jdbc.update("UPDATE m1.reminder_tasks SET status=?,attempts=?,last_error_code=?,next_retry_at=?,updated_at=? WHERE event_id=?",status,attempts,error,Timestamp.from(next),Timestamp.from(clock.instant()),event);
 }
 private static Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():((OffsetDateTime)value).toInstant();}
 private record Event(String eventId,String recipientId,String sourceModule,String sourceId,String title,String body,String titleEn,String bodyEn,String scheduledAt) {}
}
