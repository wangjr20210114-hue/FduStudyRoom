package com.campus.resource.application;
import com.campus.common.error.ApiException;
import static com.campus.resource.application.ResourceStore.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
/** Integrates immutable state windows; current active flags are never used to reconstruct the past. */
@Service public class ResourceStatistics {
    private final ResourceStore s;
    public ResourceStatistics(ResourceStore s){
        this.s=s;
    }
    public Instant now(){
        return s.now();
    }
    public Map<String,Object> planned(Instant from,Instant to,UUID roomId){
        var result=calculate(from,to,to,roomId);
        result.put("algorithm","PLANNED_CAPACITY_CURRENT_KNOWN_STATE_V1");
        result.put("kind","PLANNED_CAPACITY");
        result.put("snapshotAt",now().toString());
        for(String key:List.of("occupiedSeatSeconds","occupiedM1SeatSeconds","occupiedM2SeatSeconds","confirmedBookingCount","confirmedM1BookingCount","confirmedM2BookingCount"))result.put(key,null);
        return result;
    }
    public Map<String,Object> calculate(Instant from,Instant to,Instant asOf,UUID roomId){
        if(from==null||to==null||asOf==null||!to.isAfter(from))throw new ApiException(400,"INVALID_INTERVAL");
        if(roomId!=null)s.room(roomId,false);
        var all=s.events("WHERE NOT replay ORDER BY applied_at,source_version,operation_id");
        var rooms=roomId==null?s.db.queryForList("SELECT id FROM resource.rooms",UUID.class):List.of(roomId);
        Map<UUID,Instant> first=new HashMap<>();
        for(var e:all)if(e.kind().equals("ROOM"))first.merge(e.sourceId(),e.at(),(a,b)->a.isBefore(b)?a:b);
        Instant known=rooms.stream().map(first::get).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        boolean complete=rooms.stream().allMatch(x->{
            var initial=all.stream().filter(e->e.kind().equals("ROOM")&&e.sourceId().equals(x)).findFirst().orElse(null);return initial!=null&&((initial.version()==1&&!initial.fingerprint().equals("0".repeat(64)))||!initial.at().isAfter(from));
        });
        var result=map("from",from.toString(),"to",to.toString(),"asOf",asOf.toString(),"roomId",roomId,"algorithm","HISTORICAL_SERVICE_WINDOW_INTERSECTION_V1","historyAvailableFrom",known==null?null:known.toString(),"historyComplete",complete,"capacitySeatSeconds",null,"occupiedSeatSeconds",null,"occupiedM1SeatSeconds",null,"occupiedM2SeatSeconds",null,"confirmedBookingCount",null,"confirmedM1BookingCount",null,"confirmedM2BookingCount",null);
        if(!complete)return result;
        Instant end=to.isBefore(asOf)?to:asOf;
        double capacity=0,m1=0,m2=0;
        Set<String> m1Ids=new HashSet<>(),m2Ids=new HashSet<>();
        TreeSet<Instant> points=new TreeSet<>();
        points.add(from);
        points.add(end);
        for(var e:all)if(!e.at().isBefore(from)&&e.at().isBefore(end))points.add(e.at());
        // Opening boundaries are history-dependent, including overnight and all-day schedules.
        for(var e:all){
            if(e.kind().equals("ROOM")&&rooms.contains(e.roomId())){
                LocalDate a=from.atZone(ResourceOperations.ZONE).toLocalDate().minusDays(1),b=end.atZone(ResourceOperations.ZONE).toLocalDate().plusDays(1);
                for(LocalDate day=a;!day.isAfter(b);day=day.plusDays(1))for(String key:List.of("openTime","closeTime")){
                    Instant x=day.atTime(LocalTime.parse(text(e.data(),key))).atZone(ResourceOperations.ZONE).toInstant();
                    if(x.isAfter(from)&&x.isBefore(end))points.add(x);
                }
            }
            if(e.kind().equals("ALLOCATE")){
                for(String key:List.of("startsAt","endsAt")){
                    Instant x=instant(e.data(),key);
                    if(x.isAfter(from)&&x.isBefore(end))points.add(x);
                }
            }
        }
        if(end.isAfter(from)){
            var edges=new ArrayList<>(points);
            for(int i=0;i+1<edges.size();i++){
                Instant a=edges.get(i),b=edges.get(i+1);
                if(!b.isAfter(a))continue;
                double seconds=Duration.between(a,b).toNanos()/1_000_000_000d;
                Map<UUID,Map<String,Object>> roomStates=new HashMap<>(),seatStates=new HashMap<>();
                Map<String,ResourceStore.Event> sources=new HashMap<>();
                for(var e:all){
                    if(e.at().isAfter(a))break;
                    if(e.kind().equals("ROOM"))roomStates.put(e.sourceId(),e.data());
                    else if(e.kind().equals("SEAT"))seatStates.put(e.sourceId(),e.data());
                    else sources.put(e.module()+":"+e.sourceId(),e);
                }
                for(var entry:seatStates.entrySet()){
                    UUID seatId=entry.getKey();
                    var seat=entry.getValue();
                    UUID parent=uuid(seat,"roomId");
                    if(!rooms.contains(parent)||!bool(seat,"enabled",false))continue;
                    var room=roomStates.get(parent);
                    if(room==null||!bool(room,"enabled",false)||!bool(room,"bookingEnabled",false)||!ResourceOperations.open(room,a,b))continue;
                    boolean restricted=sources.values().stream().anyMatch(e->e.kind().equals("RESTRICT")&&parent.equals(e.roomId())&&bool(e.data(),"active",false));
                    if(restricted)continue;
                    capacity+=seconds;
                    for(var e:sources.values()){
                        if(!e.kind().equals("ALLOCATE")||!bool(e.data(),"active",false)||!parent.equals(e.roomId()))continue;
                        Instant start=instant(e.data(),"startsAt"),finish=instant(e.data(),"endsAt");
                        if(a.isBefore(start)||!a.isBefore(finish))continue;
                        if(e.module().equals("M1")&&seatId.equals(uuid(e.data(),"seatId"))){
                            m1+=seconds;
                            m1Ids.add(e.sourceId().toString());
                        }
                        else if(e.module().equals("M2")){
                            m2+=seconds;
                            m2Ids.add(e.sourceId().toString());
                        }
                    }
                }
            }
        }
        result.putAll(map("capacitySeatSeconds",capacity,"occupiedSeatSeconds",m1+m2,"occupiedM1SeatSeconds",m1,"occupiedM2SeatSeconds",m2,"confirmedBookingCount",m1Ids.size()+m2Ids.size(),"confirmedM1BookingCount",m1Ids.size(),"confirmedM2BookingCount",m2Ids.size()));
        return result;
    }
}
