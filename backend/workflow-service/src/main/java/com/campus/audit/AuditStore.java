package com.campus.audit;
import com.campus.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AuditStore {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final JsonMapper json;
 public record Receipt(UUID id,String eventKey,boolean duplicate){}
 public AuditStore(JdbcTemplate jdbc,PlatformTransactionManager manager,JsonMapper json){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;}
 private Object canonical(Object value){if(value instanceof Map<?,?> m){var sorted=new TreeMap<String,Object>();for(var e:m.entrySet())sorted.put(String.valueOf(e.getKey()),canonical(e.getValue()));return sorted;}if(value instanceof List<?> l)return l.stream().map(this::canonical).toList();return value;}
 private String hash(AuditEvent e){
  var m=new TreeMap<String,Object>();m.put("eventKey",e.eventKey());m.put("sourceService",e.sourceService());m.put("actorId",e.actorId()==null?null:e.actorId().toString());m.put("action",e.action());m.put("sourceModule",e.sourceModule());m.put("sourceId",e.sourceId()==null?null:e.sourceId().toString());m.put("success",e.success());m.put("occurredAt",e.occurredAt().toString());m.put("traceId",e.traceId());m.put("beforeData",canonical(e.beforeData()));m.put("afterData",canonical(e.afterData()));
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(m)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("Digest unavailable");}
 }
 private Map<String,Object> safe(Map<String,Object> raw){
  if(raw==null)return Map.of();var out=new TreeMap<String,Object>();
  for(var e:raw.entrySet()){
   String key=e.getKey();Object value=e.getValue();boolean allowed=false;
   if(key.equals("permissions"))allowed=value instanceof List<?> list&&list.size()<=200&&list.stream().allMatch(v->v instanceof String code&&code.length()<=128&&code.matches("[a-z][a-z0-9_-]*(?::[a-z][a-z0-9_-]*){1,7}"));
   else if(key.equals("roleCode"))allowed=value instanceof String code&&code.matches("[A-Z][A-Z0-9_]{0,63}");
   else if(Set.of("maxReservationHours","beforeStartReminderMinutes","afterStartReminderMinutes","checkinDeadlineMinutes","approvalTtlMinutes","repairSlaMinutes").contains(key))allowed=value instanceof Number n&&n.longValue()>0&&n.longValue()<=Integer.MAX_VALUE&&n.doubleValue()==n.longValue();
   else if(Set.of("status","reasonCode","departmentCode").contains(key))allowed=value instanceof String s&&s.matches("[A-Za-z0-9_.:-]{1,64}");
   else if(Set.of("version","authVersion","score","level").contains(key))allowed=value instanceof Number n&&n.longValue()>=0&&n.doubleValue()==n.longValue();
   else if(Set.of("enabled","canApplyRoom","hasEmail","verified","hasDepartment").contains(key))allowed=value instanceof Boolean;
   else if(Set.of("roomId","seatId","roleId").contains(key)){try{if(value instanceof String s){UUID.fromString(s);allowed=true;}}catch(IllegalArgumentException ignored){}}
   else if(Set.of("startsAt","endsAt").contains(key)){try{if(value instanceof String s){Instant.parse(s);allowed=true;}}catch(java.time.format.DateTimeParseException ignored){}}
   else continue; // Project known business scalars; never copy unknown request fields.
   if(!allowed)throw new ApiException(400,"INVALID_AUDIT_SUMMARY");out.put(key,value);
  }return out;
 }
 public Receipt accept(AuditEvent event){
  String digest=hash(event);var before=safe(event.beforeData());var after=safe(event.afterData());
  return tx.execute(status->{
   jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",event.eventKey());
   var old=jdbc.queryForList("SELECT id,payload_hash FROM support.audit_logs WHERE event_key=?",event.eventKey());
   if(!old.isEmpty()){
    String stored=(String)old.getFirst().get("payload_hash");if(!MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII)))throw new ApiException(409,"AUDIT_EVENT_CONFLICT");
    return new Receipt((UUID)old.getFirst().get("id"),event.eventKey(),true);
   }
   UUID id=UUID.randomUUID();jdbc.update("INSERT INTO support.audit_logs(id,event_key,payload_hash,source_service,actor_id,action,source_module,source_id,before_data,after_data,success,occurred_at,trace_id) VALUES(?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?)",id,event.eventKey(),digest,event.sourceService(),event.actorId(),event.action(),event.sourceModule(),event.sourceId(),json.writeValueAsString(before),json.writeValueAsString(after),event.success(),Timestamp.from(event.occurredAt()),event.traceId());return new Receipt(id,event.eventKey(),false);
  });
 }
 public Map<String,Object> list(int page,int size,String trace,String module,UUID source){
  if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE||(trace!=null&&!trace.matches("[A-Za-z0-9_.:-]{1,64}"))||(module!=null&&!Set.of("M1","M2","M3","D","EXTERNAL").contains(module)))throw new ApiException(400,"INVALID_AUDIT_QUERY");
  String where=" WHERE true";var values=new ArrayList<Object>();
  if(trace!=null){where+=" AND trace_id=?";values.add(trace);}if(module!=null){where+=" AND source_module=?";values.add(module);}if(source!=null){where+=" AND source_id=?";values.add(source);}
  long total=jdbc.queryForObject("SELECT count(*) FROM support.audit_logs"+where,Long.class,values.toArray());values.add(size);values.add(page*size);
  var rows=jdbc.query("SELECT * FROM support.audit_logs"+where+" ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",(rs,n)->{
   var row=new LinkedHashMap<String,Object>();for(String[] f:new String[][]{{"id","id"},{"eventKey","event_key"},{"sourceService","source_service"},{"actorId","actor_id"},{"action","action"},{"sourceModule","source_module"},{"sourceId","source_id"},{"success","success"},{"traceId","trace_id"}})row.put(f[0],rs.getObject(f[1]));row.put("occurredAt",rs.getTimestamp("occurred_at").toInstant());row.put("beforeData",json.readValue(rs.getString("before_data"),Map.class));row.put("afterData",json.readValue(rs.getString("after_data"),Map.class));return row;
  },values.toArray());return Map.of("items",rows,"total",total,"page",page,"size",size);
 }
}
