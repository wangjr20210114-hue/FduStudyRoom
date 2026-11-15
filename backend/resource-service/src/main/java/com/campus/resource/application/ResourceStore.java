package com.campus.resource.application;
import com.campus.common.error.ApiException;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;
/** Resource-owned persistence and immutable history. Never queries another module's tables. */
@Component public class ResourceStore {
    public final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final Clock clock;
    static final JsonMapper JSON=JsonMapper.builder().build();
    public ResourceStore(JdbcTemplate db,PlatformTransactionManager manager,@Qualifier("resourceCheckinClock") Clock clock){
        this.db=db;
        this.tx=new TransactionTemplate(manager);
        this.clock=clock;
    }
    public Instant now(){
        return clock.instant();
    }
    public <T>T transaction(Supplier<T> call){
        try{
            return tx.execute(s->call.get());
        }
        catch(DataIntegrityViolationException e){
            throw new ApiException(409,"RESOURCE_CONFLICT");
        }
    }
    public void lock(String key){
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",key);
    }
    public static Map<String,Object> map(Object...p){
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i<p.length;i+=2)m.put((String)p[i],p[i+1]);
        return m;
    }
    public static String text(Map<String,Object>m,String key){
        Object x=m.get(key);
        return x==null?null:x.toString();
    }
    public static UUID uuid(Map<String,Object>m,String key){
        try{
            return text(m,key)==null?null:UUID.fromString(text(m,key));
        }
        catch(Exception e){
            throw new ApiException(400,"INVALID_REQUEST");
        }
    }
    public static int number(Map<String,Object>m,String key){
        try{
            Object x=m.get(key);
            if(!(x instanceof Number n)||n.doubleValue()!=n.intValue())throw new Exception();
            return n.intValue();
        }
        catch(Exception e){
            throw new ApiException(400,"INVALID_REQUEST");
        }
    }
    public static boolean bool(Map<String,Object>m,String key,boolean def){
        Object x=m.get(key);
        if(x==null)return def;
        if(!(x instanceof Boolean))throw new ApiException(400,"INVALID_REQUEST");
        return (Boolean)x;
    }
    public static Instant instant(Map<String,Object>m,String key){
        try{
            return OffsetDateTime.parse(text(m,key)).toInstant();
        }
        catch(Exception e){
            throw new ApiException(400,"INVALID_INTERVAL");
        }
    }
    public String fingerprint(Map<String,Object>m){
        try{
            var sorted=new TreeMap<>(m);
            sorted.remove("operationId");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(sorted)));
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> read(String s){
        return JSON.readValue(s,Map.class);
    }
    public record Event(UUID operationId,String module,UUID sourceId,int version,String kind,UUID roomId,String fingerprint,Map<String,Object> data,Instant at,boolean replay){
    }
    public List<Event> events(String suffix,Object...args){
        return db.query("SELECT * FROM resource.resource_operations "+suffix,(rs,n)->new Event(rs.getObject("operation_id",UUID.class),rs.getString("source_module"),rs.getObject("source_id",UUID.class),rs.getInt("source_version"),rs.getString("kind"),rs.getObject("room_id",UUID.class),rs.getString("fingerprint"),read(rs.getString("snapshot")),rs.getTimestamp("applied_at").toInstant(),rs.getBoolean("replay")),args);
    }
    public Event latest(String module,UUID id){
        var a=events("WHERE source_module=? AND source_id=? AND NOT replay AND kind IN('ALLOCATE','RELEASE','RESTRICT') ORDER BY source_version DESC,applied_at DESC LIMIT 1",module,id);
        return a.isEmpty()?null:a.getFirst();
    }
    public Event operation(UUID id){
        var a=events("WHERE operation_id=?",id);
        return a.isEmpty()?null:a.getFirst();
    }
    public void event(UUID op,String module,UUID source,int version,String kind,UUID room,String fp,Map<String,Object>data,Instant at,boolean replay){
        Object principal=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()==null?null:org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        event(op,module,source,version,kind,room,fp,data,at,replay,principal instanceof com.campus.common.security.IdentityPrincipal p?p.id():null);
    }
    public void event(UUID op,String module,UUID source,int version,String kind,UUID room,String fp,Map<String,Object>data,Instant at,boolean replay,UUID actor){
        db.update("INSERT INTO resource.resource_operations(operation_id,source_module,source_id,source_version,kind,room_id,fingerprint,snapshot,applied_at,replay) VALUES(?,?,?,?,?,?,?,?::jsonb,?,?)",op,module,source,version,kind,room,fp,JSON.writeValueAsString(data),OffsetDateTime.ofInstant(at,ZoneOffset.UTC),replay);
        if(!replay){
            var projection=map("roomId",room,"version",version,"enabled",bool(data,"active",bool(data,"enabled",false)),"status",data.containsKey("status")?text(data,"status"):"APPLIED");
            if(data.get("seatId")!=null)projection.put("seatId",data.get("seatId"));
            String trace=text(data,"requestTraceId");
            if(trace==null&&org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attributes){
                Object value=attributes.getRequest().getAttribute("traceId");
                if(value instanceof String id)trace=id;
            }
            if(trace==null||!trace.matches("[0-9a-fA-F-]{36}"))trace=UUID.randomUUID().toString();
            var payload=map("eventKey","M02:"+op,"sourceService","resource-service","actorId",actor,"action","RESOURCE_"+kind,"sourceModule","D","sourceId",source,"success",!kind.equals("DENIED"),"occurredAt",at.toString(),"traceId",trace,"beforeData",Map.of(),"afterData",projection);
            db.update("UPDATE resource.resource_operations SET audit_state='PENDING',audit_payload=?::jsonb,audit_next=? WHERE operation_id=?",JSON.writeValueAsString(payload),OffsetDateTime.ofInstant(at,ZoneOffset.UTC),op);
        }
    }
    public void management(String kind,UUID id,int version,UUID room,Map<String,Object>data){
        event(UUID.randomUUID(),"ADMIN",id,version,kind,room,fingerprint(data),data,now(),false);
    }
    public Map<String,Object> room(UUID id,boolean lock){
        var a=db.query("SELECT * FROM resource.rooms WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->map("id",rs.getObject("id",UUID.class),"code",rs.getString("code"),"name",rs.getString("name"),"campus",rs.getString("campus"),"building",rs.getString("building"),"capacity",rs.getInt("capacity"),"departmentCode",rs.getString("department_code"),"openTime",rs.getObject("open_time",LocalTime.class).toString(),"closeTime",rs.getObject("close_time",LocalTime.class).toString(),"bookingEnabled",rs.getBoolean("booking_enabled"),"enabled",rs.getBoolean("enabled"),"version",rs.getInt("version")),id);
        if(a.isEmpty())throw new ApiException(404,"ROOM_NOT_FOUND");
        return a.getFirst();
    }
    public Map<String,Object> seat(UUID id){
        var a=db.query("SELECT * FROM resource.seats WHERE id=?",(rs,n)->map("id",rs.getObject("id",UUID.class),"seatId",rs.getObject("id",UUID.class),"roomId",rs.getObject("room_id",UUID.class),"code",rs.getString("code"),"socketType",rs.getString("socket_type"),"hasSocket",!rs.getString("socket_type").equals("NONE"),"enabled",rs.getBoolean("enabled"),"version",rs.getInt("version")),id);
        if(a.isEmpty())throw new ApiException(404,"SEAT_NOT_FOUND");
        return a.getFirst();
    }
    public void range(UUID seat,String module,UUID source,int version,UUID user,Instant from,Instant to){
        db.update("INSERT INTO resource.seat_allocations(seat_id,source_module,source_id,source_version,user_id,occupied_period,active) VALUES(?,?,?,?,?,tstzrange(?::timestamptz,?::timestamptz,'[)'),true) ON CONFLICT(source_module,source_id,seat_id) DO UPDATE SET occupied_period=EXCLUDED.occupied_period,source_version=EXCLUDED.source_version,user_id=EXCLUDED.user_id,active=true,updated_at=now()",seat,module,source,version,user,from.toString(),to.toString());
    }
    public void inherit(UUID room,UUID seat){
        for(var e:events("WHERE room_id=? AND source_module='M2' AND kind='ALLOCATE' AND NOT replay",room)){
            var latest=latest("M2",e.sourceId());
            if(latest!=null&&latest.operationId().equals(e.operationId())&&bool(latest.data(),"active",false)){
                db.update("INSERT INTO resource.seat_allocations(seat_id,source_module,source_id,source_version,user_id,occupied_period,active) VALUES(?,'M2',?,?,NULL,tstzrange(?::timestamptz,?::timestamptz,'[)'),true) ON CONFLICT(source_module,source_id,seat_id) DO NOTHING",seat,e.sourceId(),e.version(),text(e.data(),"startsAt"),text(e.data(),"endsAt"));
            }
        }
    }
}
