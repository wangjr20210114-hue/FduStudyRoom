package com.campus.reservation;
import com.campus.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

final class ReservationStore {
 final JdbcTemplate db;final TransactionTemplate tx;final JsonMapper json=JsonMapper.builder().build();
 ReservationStore(DataSource ds){db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));}
 <T>T transaction(Supplier<T> body){return tx.execute(status->body.get());}
 static Timestamp ts(Instant t){return Timestamp.from(t);}
 static Instant instant(Object v){return v==null?null:v instanceof Timestamp t?t.toInstant():v instanceof OffsetDateTime o?o.toInstant():Instant.parse(v.toString());}
 static Map<String,Object> map(Object...args){Map<String,Object>r=new LinkedHashMap<>();for(int i=0;i<args.length;i+=2)r.put((String)args[i],args[i+1]);return r;}
 Map<String,Object> parse(Object value){return value==null?Map.of():json.readValue(value.toString(),Map.class);}
 String text(Object value){return json.writeValueAsString(value);}
 Object canonical(Object value){if(value instanceof Map<?,?>m){Map<String,Object>s=new TreeMap<>();m.forEach((k,v)->s.put(k.toString(),canonical(v)));return s;}if(value instanceof Collection<?>c)return c.stream().map(this::canonical).toList();return value;}
 String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text(canonical(value)).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 void lock(String key){db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","M03:"+key);}
 Map<String,Object> row(UUID id,boolean locked){var rows=db.queryForList("SELECT * FROM m1.seat_reservations WHERE id=?"+(locked?" FOR UPDATE":""),id);if(rows.isEmpty())throw new ApiException(404,"RESERVATION_NOT_FOUND");return rows.getFirst();}
 UUID command(UUID op,UUID actor,String action,String fingerprint){var rows=db.queryForList("SELECT * FROM m1.reservation_commands WHERE operation_id=?",op);if(rows.isEmpty())return null;var r=rows.getFirst();if(!actor.equals(r.get("actor_id"))||!action.equals(r.get("action"))||!fingerprint.equals(r.get("fingerprint")))throw new ApiException(409,"RESERVATION_OPERATION_CONFLICT");return (UUID)r.get("reservation_id");}
 void command(UUID op,UUID id,UUID actor,String action,String fingerprint,Instant now){db.update("INSERT INTO m1.reservation_commands(operation_id,reservation_id,actor_id,action,fingerprint,created_at) VALUES(?,?,?,?,?,?)",op,id,actor,action,fingerprint,ts(now));}
 void enqueue(UUID event,UUID id,String kind,Object payload,boolean frozen,String reminder,Instant due,Instant now){db.update("INSERT INTO m1.reservation_outbox(id,reservation_id,kind,payload,frozen,reminder_kind,next_retry_at,created_at) VALUES(?,?,?,?::jsonb,?,?,?,?) ON CONFLICT(id) DO NOTHING",event,id,kind,text(payload),frozen,reminder,ts(due),ts(now));}
 Map<String,Object> view(Map<String,Object>r){var room=parse(r.get("room_snapshot"));var seat=parse(r.get("seat_snapshot"));return map("id",r.get("id"),"userId",r.get("user_id"),"roomId",r.get("room_id"),"seatId",r.get("seat_id"),"roomName",room.get("name"),"roomCode",room.get("code"),"seatCode",seat.get("code"),"startsAt",instant(r.get("starts_at")),"endsAt",instant(r.get("ends_at")),"status",r.get("status"),"checkedInAt",instant(r.get("checked_in_at")),"checkinDeadlineAt",instant(r.get("checkin_deadline_at")),"cancelReason",r.get("cancel_reason"),"cancelledAt",instant(r.get("cancelled_at")),"policySnapshot",parse(r.get("policy_snapshot")),"resourceState",r.get("resource_state"),"resourceVersion",r.get("resource_version"),"version",r.get("version"),"failureCode",r.get("failure_code"),"metadataAvailable",r.get("room_id")!=null,"timeZone","Asia/Shanghai");}
}
