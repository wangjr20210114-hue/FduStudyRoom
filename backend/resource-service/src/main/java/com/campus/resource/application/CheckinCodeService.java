package com.campus.resource.application;

import com.campus.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckinCodeService {
    public record Display(UUID roomId, String roomName, LocalDate date, String code) {}
    public record RoomDetails(UUID id, String code, String name, int capacity, boolean enabled) {}
    private record Room(UUID id,String roomCode,String name,int capacity,boolean enabled,String checkinCode,LocalDate checkinDate) {}
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();

    public CheckinCodeService(JdbcTemplate jdbc,PlatformTransactionManager manager,@org.springframework.beans.factory.annotation.Qualifier("resourceCheckinClock") Clock clock) {
        this.jdbc=jdbc;this.transactions=new TransactionTemplate(manager);this.clock=clock;
    }
    private LocalDate today(){return LocalDate.ofInstant(clock.instant(),SHANGHAI);}
    private Room room(UUID id,boolean lock) {
        var rows=jdbc.query("SELECT id,code,name,capacity,enabled,checkin_code,checkin_code_date FROM resource.rooms WHERE id=?"+(lock?" FOR UPDATE":""),
            (rs,n)->new Room(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getInt("capacity"),rs.getBoolean("enabled"),rs.getString("checkin_code"),rs.getObject("checkin_code_date",LocalDate.class)),id);
        if(rows.isEmpty())throw new ApiException(404,"ROOM_NOT_FOUND");
        return rows.getFirst();
    }
    private void requireEnabled(Room room){if(!room.enabled())throw new ApiException(409,"ROOM_DISABLED");}
    public RoomDetails details(UUID id){var room=room(id,false);return new RoomDetails(room.id(),room.roomCode(),room.name(),room.capacity(),room.enabled());}
    public Display display(UUID id) {
        return transactions.execute(status->{
            // Serialize generation across rooms so another room cannot receive the same current code.
            jdbc.execute("SELECT pg_advisory_xact_lock(140207)");
            var room=room(id,true);requireEnabled(room);LocalDate date=today();
            if(date.equals(room.checkinDate())&&room.checkinCode()!=null)return new Display(room.id(),room.name(),date,room.checkinCode());
            String code=null;
            for(int attempts=0;attempts<128;attempts++){
                String candidate=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
                if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM resource.rooms WHERE checkin_code=?)",Boolean.class,candidate))){code=candidate;break;}
            }
            if(code==null)throw new ApiException(503,"CHECKIN_CODE_UNAVAILABLE");
            jdbc.update("UPDATE resource.rooms SET checkin_code=?,checkin_code_date=?,updated_at=? WHERE id=?",code,date,OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),id);
            return new Display(room.id(),room.name(),date,code);
        });
    }
    public boolean verify(UUID id,LocalDate date,String code){
        var room=room(id,false);requireEnabled(room);
        return date.equals(today()) && date.equals(room.checkinDate()) && room.checkinCode()!=null
            && MessageDigest.isEqual(room.checkinCode().getBytes(StandardCharsets.US_ASCII),code.getBytes(StandardCharsets.US_ASCII));
    }
}
