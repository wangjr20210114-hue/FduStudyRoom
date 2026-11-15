package com.campus.resource.application;
import com.campus.common.error.ApiException;
import static com.campus.resource.application.ResourceStore.*;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
@Service public class ResourceCatalogue {
    final ResourceStore s;
    public ResourceCatalogue(ResourceStore s){
        this.s=s;
    }
    public List<Map<String,Object>> rooms(){
        return s.db.queryForList("SELECT id FROM resource.rooms ORDER BY campus,building,code").stream().map(x->s.room((UUID)x.get("id"),false)).toList();
    }
    public Map<String,Object> seatDetails(UUID id){
        return s.seat(id);
    }
    public Map<String,Object> details(UUID id){
        return s.room(id,false);
    }
    String required(Map<String,Object>m,String key,int max){
        String x=text(m,key);
        if(x==null||x.isBlank()||x.length()>max)throw new ApiException(400,"INVALID_REQUEST");
        return x.trim();
    }
    public Map<String,Object> saveRoom(UUID id,Map<String,Object>b){
        return s.transaction(()->{
            String code=required(b,"code",64),name=required(b,"name",128),campus=required(b,"campus",64),building=required(b,"building",128);int cap=number(b,"capacity");if(cap<=0)throw new ApiException(400,"INVALID_CAPACITY");LocalTime open,close;try{
                open=LocalTime.parse(required(b,"openTime",16));close=LocalTime.parse(required(b,"closeTime",16));
            }
            catch(Exception e){
                throw new ApiException(400,"INVALID_OPENING_TIME");
            }
            boolean enabled=bool(b,"enabled",true),booking=bool(b,"bookingEnabled",false);String department=text(b,"departmentCode");if(department!=null&&(department.isBlank()||department.length()>64))throw new ApiException(400,"INVALID_DEPARTMENT");UUID room=id;int version=1;  if(room==null){
                room=UUID.randomUUID();s.db.update("INSERT INTO resource.rooms(id,code,name,campus,building,capacity,department_code,open_time,close_time,booking_enabled,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?)",room,code,name,campus,building,cap,department,open,close,booking,enabled);
            }
            else{
                var old=s.room(room,true);if(number(b,"expectedVersion")!=number(old,"version"))throw new ApiException(409,"STALE_RESOURCE_VERSION");if(cap<s.db.queryForObject("SELECT count(*) FROM resource.seats WHERE room_id=? AND enabled",Integer.class,room))throw new ApiException(409,"CAPACITY_BELOW_SEAT_COUNT");version=number(old,"version")+1;s.db.update("UPDATE resource.rooms SET code=?,name=?,campus=?,building=?,capacity=?,department_code=?,open_time=?,close_time=?,booking_enabled=?,enabled=?,version=?,updated_at=? WHERE id=?",code,name,campus,building,cap,department,open,close,booking,enabled,version,OffsetDateTime.ofInstant(s.now(),ZoneOffset.UTC),room);
            }
            var result=s.room(room,false);s.management("ROOM",room,version,room,result);return result;
        });
    }
    public List<Map<String,Object>> seats(UUID room,Boolean socket){
        s.room(room,false);
        return s.db.queryForList("SELECT id FROM resource.seats WHERE room_id=? ORDER BY code",UUID.class,room).stream().map(s::seat).filter(x->socket==null||bool(x,"hasSocket",false)==socket).toList();
    }
    public Map<String,Object> saveSeat(UUID roomId,UUID id,Map<String,Object>b){
        return s.transaction(()->{
            String code=required(b,"code",64),socket=required(b,"socketType",16);if(!Set.of("NONE","FIXED","RAIL").contains(socket))throw new ApiException(400,"INVALID_SOCKET_TYPE");boolean enabled=bool(b,"enabled",true);UUID room=roomId;Map<String,Object> old=null;if(id!=null){
                old=s.seat(id);room=uuid(old,"roomId");
            }
            var parent=s.room(room,true);int version=1;if(old!=null&&number(b,"expectedVersion")!=number(old,"version"))throw new ApiException(409,"STALE_RESOURCE_VERSION");int count=s.db.queryForObject("SELECT count(*) FROM resource.seats WHERE room_id=? AND enabled",Integer.class,room);if(enabled&&(old==null||!bool(old,"enabled",false))&&count>=number(parent,"capacity"))throw new ApiException(409,"ROOM_CAPACITY_EXCEEDED");UUID seat=id;if(seat==null){
                seat=UUID.randomUUID();s.db.update("INSERT INTO resource.seats(id,room_id,code,socket_type,enabled) VALUES(?,?,?,?,?)",seat,room,code,socket,enabled);
            }
            else{
                version=number(old,"version")+1;s.db.update("UPDATE resource.seats SET code=?,socket_type=?,enabled=?,version=?,updated_at=? WHERE id=?",code,socket,enabled,version,OffsetDateTime.ofInstant(s.now(),ZoneOffset.UTC),seat);
            }
            if(enabled)s.inherit(room,seat);var result=s.seat(seat);s.management("SEAT",seat,version,room,result);return result;
        });
    }
}
