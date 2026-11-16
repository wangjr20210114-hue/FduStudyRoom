package com.campus.resource.application;
import com.campus.common.error.ApiException;
import static com.campus.resource.application.ResourceStore.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service public class ResourceOperations {
    private final ResourceStore s;
    private final ResourceEligibility identity;
    public ResourceOperations(ResourceStore s,ResourceEligibility identity){
        this.s=s;
        this.identity=identity;
    }
    public record Write(Map<String,Object> result,boolean duplicate){
    }
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public static boolean open(Map<String,Object>room,Instant from,Instant to){
        LocalTime begin=LocalTime.parse(text(room,"openTime")),end=LocalTime.parse(text(room,"closeTime"));
        if(begin.equals(end))return true;
        ZonedDateTime start=from.atZone(ZONE);
        LocalDate date=start.toLocalDate();
        if(begin.isAfter(end)&&start.toLocalTime().isBefore(end))date=date.minusDays(1);
        Instant a=date.atTime(begin).atZone(ZONE).toInstant(),b=(begin.isBefore(end)?date:date.plusDays(1)).atTime(end).atZone(ZONE).toInstant();
        return !from.isBefore(a)&&!to.isAfter(b);
    }
    public void eligible(Map<String,Object>room,Map<String,Object>user){
        String department=text(room,"departmentCode");
        if(department!=null&&!department.equals(text(user,"departmentCode")))throw new ApiException(403,"DEPARTMENT_NOT_ELIGIBLE");
    }
    void availableRoom(Map<String,Object>room,Instant from,Instant to){
        if(!bool(room,"enabled",false)||!bool(room,"bookingEnabled",false))throw new ApiException(409,"ROOM_DISABLED");
        if(!open(room,from,to))throw new ApiException(409,"OUTSIDE_OPENING_HOURS");
        if(Boolean.TRUE.equals(s.db.queryForObject("SELECT EXISTS(SELECT 1 FROM resource.room_restrictions WHERE room_id=? AND active AND blocked_period && tstzrange(?::timestamptz,?::timestamptz,'[)'))",Boolean.class,uuid(room,"id"),from.toString(),to.toString())))throw new ApiException(409,"ROOM_RESTRICTED");
    }
    public Write allocate(Map<String,Object>input){
        var b=new LinkedHashMap<>(input);
        String module=text(b,"sourceModule");
        if(!Set.of("M1","M2").contains(module==null?"":module))throw new ApiException(400,"INVALID_SOURCE_MODULE");
        UUID source=uuid(b,"sourceId"),room=uuid(b,"roomId"),seat=uuid(b,"seatId"),user=uuid(b,"userId");
        Instant from=instant(b,"startsAt"),to=instant(b,"endsAt");
        if(source==null||room==null||!to.isAfter(from)||number(b,"sourceVersion")<1||("M1".equals(module)&&(seat==null||user==null)))throw new ApiException(400,"INVALID_ALLOCATION");
        // Canonicalize known fields; a caller-supplied department is never trusted.
        var request=map("sourceModule",module,"sourceId",source,"sourceVersion",number(b,"sourceVersion"),"roomId",room,"seatId","M1".equals(module)?seat:null,"userId","M1".equals(module)?user:null,"startsAt",from.toString(),"endsAt",to.toString(),"kind","ALLOCATE");
        UUID operation=uuid(b,"operationId");
        if(operation==null)throw new ApiException(400,"OPERATION_ID_REQUIRED");
        var cached=s.operation(operation);
        if(cached!=null){
            if(!cached.fingerprint().equals(s.fingerprint(request)))throw new ApiException(409,"IDEMPOTENCY_CONFLICT");
            return new Write(cached.data(),true);
        }
        var trustedUser="M1".equals(module)?identity.get(user):null;
        return write(operation,module,source,number(b,"sourceVersion"),"ALLOCATE",room,request,()->{
            var r=s.room(room,true);availableRoom(r,from,to);if("M1".equals(module))eligible(r,trustedUser);var seats="M1".equals(module)?List.of(seat):s.db.queryForList("SELECT id FROM resource.seats WHERE room_id=? AND enabled ORDER BY id",UUID.class,room);if(seats.isEmpty())throw new ApiException(409,"NO_ENABLED_SEATS");for(UUID id:seats){
                var data=s.seat(id);if(!room.equals(uuid(data,"roomId"))||!bool(data,"enabled",false))throw new ApiException(409,"SEAT_DISABLED");
            }
            s.db.update("UPDATE resource.seat_allocations SET active=false,updated_at=? WHERE source_module=? AND source_id=?",OffsetDateTime.ofInstant(s.now(),ZoneOffset.UTC),module,source);for(UUID id:seats)s.range(id,module,source,number(b,"sourceVersion"),"M1".equals(module)?user:null,from,to);var result=new LinkedHashMap<>(request);result.remove("kind");result.put("active",true);result.put("allocationCount",seats.size());return result;
        });
    }
    public Write release(Map<String,Object>b){
        String module=text(b,"sourceModule");
        UUID source=uuid(b,"sourceId");
        int version=number(b,"sourceVersion");
        if(!Set.of("M1","M2").contains(module==null?"":module)||source==null||version<1)throw new ApiException(400,"INVALID_RELEASE");
        var request=map("sourceModule",module,"sourceId",source,"sourceVersion",version,"kind","RELEASE");
        return write(uuid(b,"operationId"),module,source,version,"RELEASE",null,request,()->{
            var old=s.latest(module,source);if(old!=null&&old.roomId()!=null)s.room(old.roomId(),true);s.db.update("UPDATE resource.seat_allocations SET active=false,source_version=?,updated_at=? WHERE source_module=? AND source_id=?",version,OffsetDateTime.ofInstant(s.now(),ZoneOffset.UTC),module,source);var result=old==null?new LinkedHashMap<String,Object>():new LinkedHashMap<>(old.data());result.putAll(map("sourceModule",module,"sourceId",source,"sourceVersion",version,"active",false));return result;
        });
    }
    public Write restrict(UUID source,Map<String,Object>b){
        UUID room=uuid(b,"roomId");
        int version=number(b,"sourceVersion");
        boolean active=bool(b,"active",false);
        if(source==null||room==null||!(b.get("active") instanceof Boolean)||version<1)throw new ApiException(400,"INVALID_RESTRICTION");
        var request=map("sourceModule","M3","sourceId",source,"sourceVersion",version,"roomId",room,"active",active,"kind","RESTRICT");
        return write(uuid(b,"operationId"),"M3",source,version,"RESTRICT",room,request,()->{
            s.room(room,true);var old=s.latest("M3",source);if(old!=null&&old.roomId()!=null&&!room.equals(old.roomId()))throw new ApiException(409,"SOURCE_TARGET_CHANGED");s.db.update("INSERT INTO resource.room_restrictions(room_id,blocked_period,source_type,source_id,source_version,reason,active) VALUES(?,tstzrange(?::timestamptz,NULL,'[)'),'M3',?,?,'MAINTENANCE',?) ON CONFLICT(source_type,source_id) DO UPDATE SET active=EXCLUDED.active,blocked_period=CASE WHEN EXCLUDED.active THEN EXCLUDED.blocked_period ELSE resource.room_restrictions.blocked_period END,source_version=EXCLUDED.source_version,updated_at=now()",room,s.now().toString(),source,version,active);var result=new LinkedHashMap<>(request);result.remove("kind");return result;
        });
    }
    Write write(UUID op,String module,UUID source,int version,String kind,UUID room,Map<String,Object>request,java.util.function.Supplier<Map<String,Object>> action){
        if(op==null)throw new ApiException(400,"OPERATION_ID_REQUIRED");
        String fp=s.fingerprint(request);
        return s.transaction(()->{
            s.lock("operation:"+op);s.lock("source:"+module+":"+source);var prior=s.operation(op);if(prior!=null){
                if(!prior.fingerprint().equals(fp))throw new ApiException(409,"IDEMPOTENCY_CONFLICT");return new Write(prior.data(),true);
            }
            var latest=s.latest(module,source);if(latest!=null){
                if(version<latest.version())throw new ApiException(409,"STALE_SOURCE_VERSION");if(version==latest.version()){
                    if(!latest.fingerprint().equals(fp))throw new ApiException(409,"SOURCE_VERSION_CONFLICT");s.event(op,module,source,version,kind,latest.roomId(),fp,latest.data(),s.now(),true);return new Write(latest.data(),true);
                }
            }
            var result=action.get();result.put("operationId",op);Instant effective=s.now();result.put("effectiveAt",effective.toString());UUID actualRoom=room!=null?room:uuid(result,"roomId");s.event(op,module,source,version,kind,actualRoom,fp,result,effective,false);return new Write(result,false);
        });
    }
    public Map<String,Object> source(String module,UUID id){
        if(!Set.of("M1","M2","M3").contains(module))throw new ApiException(400,"INVALID_SOURCE_MODULE");
        var source=s.latest(module,id);
        if(source==null)throw new ApiException(404,"SOURCE_NOT_FOUND");
        return source.data();
    }
    public List<Map<String,Object>> availability(Instant from,Instant to,UUID user,String campus,String building,Boolean socket){
        if(!to.isAfter(from))throw new ApiException(400,"INVALID_INTERVAL");
        var principal=identity.get(user);
        List<Map<String,Object>>out=new ArrayList<>();
        for(UUID id:s.db.queryForList("SELECT id FROM resource.seats WHERE enabled ORDER BY room_id,code",UUID.class)){
            var seat=s.seat(id);
            var room=s.room(uuid(seat,"roomId"),false);
            if(campus!=null&&!campus.equals(text(room,"campus"))||building!=null&&!building.equals(text(room,"building"))||socket!=null&&bool(seat,"hasSocket",false)!=socket)continue;
            try{
                availableRoom(room,from,to);
                eligible(room,principal);
            }
            catch(ApiException e){
                continue;
            }
            if(Boolean.TRUE.equals(s.db.queryForObject("SELECT EXISTS(SELECT 1 FROM resource.seat_allocations WHERE active AND (seat_id=? OR (source_module='M1' AND user_id=?)) AND occupied_period && tstzrange(?::timestamptz,?::timestamptz,'[)'))",Boolean.class,id,user,from.toString(),to.toString())))continue;
            var result=new LinkedHashMap<>(seat);
            result.putAll(map("roomCode",text(room,"code"),"roomName",text(room,"name"),"campus",text(room,"campus"),"building",text(room,"building")));
            out.add(result);
        }
        return out;
    }
}
