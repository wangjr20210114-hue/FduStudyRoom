package com.campus.notification;

import com.campus.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class NotificationStore {
    public record Receipt(UUID eventId,UUID notificationId,boolean duplicate,String inAppStatus,String emailStatus){}
    public record Delivery(UUID id,UUID eventId,String address,String title,String body,int attempts){}
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final JsonMapper json;
    public NotificationStore(JdbcTemplate jdbc,PlatformTransactionManager manager,JsonMapper json){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;}
    private void eventLock(UUID id){jdbc.query("SELECT pg_advisory_xact_lock(?)",rs->{},id.getMostSignificantBits()^id.getLeastSignificantBits());}
    private String hash(NotificationEvent event){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(event)));}catch(Exception error){throw new IllegalStateException("Cannot encode notification event",error);}}
    public Receipt accept(NotificationEvent event,boolean smtpConfigured){return tx.execute(status->{
        eventLock(event.eventId());String hash=hash(event);
        var hashes=jdbc.queryForList("SELECT payload_hash FROM support.notifications WHERE event_id=? AND channel='IN_APP'",String.class,event.eventId());
        if(!hashes.isEmpty()){if(!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),hashes.getFirst().getBytes(StandardCharsets.US_ASCII)))throw new ApiException(409,"NOTIFICATION_EVENT_CONFLICT");return receipt(event.eventId(),true);}
        Instant now=Instant.now();boolean due=!event.scheduledAt().isAfter(now);
        for(String channel:List.of("IN_APP","EMAIL")){
            String state=channel.equals("IN_APP")?(due?"SENT":"PENDING"):(smtpConfigured&&event.emailAddress()!=null&&!event.emailAddress().isBlank()?"PENDING":"NOT_CONFIGURED");
            jdbc.update("INSERT INTO support.notifications(id,recipient_id,channel,event_key,event_id,payload_hash,source_module,source_id,title,body,title_en,body_en,email_address,scheduled_at,status,sent_at,last_error_code) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),event.recipientId(),channel,event.eventId()+":"+channel,event.eventId(),hash,event.sourceModule(),event.sourceId(),event.title(),event.body(),event.titleEn(),event.bodyEn(),event.emailAddress(),Timestamp.from(event.scheduledAt()),state,state.equals("SENT")?Timestamp.from(now):null,state.equals("NOT_CONFIGURED")?(event.emailAddress()==null||event.emailAddress().isBlank()?"EMAIL_ADDRESS_MISSING":"SMTP_NOT_CONFIGURED"):null);
        }
        return receipt(event.eventId(),false);
    });}
    Receipt receipt(UUID event,boolean duplicate){
        var rows=jdbc.queryForList("SELECT id,channel,status FROM support.notifications WHERE event_id=?",event);if(rows.isEmpty())throw new ApiException(404,"NOTIFICATION_NOT_FOUND");
        UUID id=null;String app=null,email=null;for(var row:rows){if(row.get("channel").equals("IN_APP")){id=(UUID)row.get("id");app=(String)row.get("status");}else email=(String)row.get("status");}return new Receipt(event,id,duplicate,app,email);
    }
    public Receipt cancel(UUID event){return tx.execute(status->{eventLock(event);receipt(event,false);
        jdbc.update("UPDATE support.notifications SET status='CANCELLED',next_retry_at=NULL,updated_at=now() WHERE event_id=? AND status IN ('PENDING','FAILED','NOT_CONFIGURED')",event);return receipt(event,false);
    });}
    public Map<String,Object> list(UUID recipient,int page,int size,boolean english){
        if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw new ApiException(400,"INVALID_NOTIFICATION_PAGE");
        var rows=jdbc.query("SELECT a.*,e.status AS email_status,e.last_error_code AS email_error,e.next_retry_at AS email_retry FROM support.notifications a JOIN support.notifications e ON e.event_id=a.event_id AND e.channel='EMAIL' WHERE a.recipient_id=? AND a.channel='IN_APP' ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",(rs,index)->{
            var m=new LinkedHashMap<String,Object>();for(String[] pair:new String[][]{{"id","id"},{"eventId","event_id"},{"recipientId","recipient_id"},{"sourceModule","source_module"},{"sourceId","source_id"},{"status","status"},{"emailStatus","email_status"},{"lastErrorCode","email_error"}})m.put(pair[0],rs.getObject(pair[1]));
            m.put("title",rs.getString(english?"title_en":"title"));m.put("body",rs.getString(english?"body_en":"body"));m.put("locale",english?"en":"zh-CN");
            for(String[] pair:new String[][]{{"scheduledAt","scheduled_at"},{"readAt","read_at"},{"nextRetryAt","email_retry"}}){Timestamp stamp=rs.getTimestamp(pair[1]);m.put(pair[0],stamp==null?null:stamp.toInstant());}return m;
        },recipient,size,page*size);
        long total=jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE recipient_id=? AND channel='IN_APP'",Long.class,recipient);return Map.of("items",rows,"page",page,"size",size,"total",total);
    }
    public void read(UUID recipient,UUID id){if(jdbc.update("UPDATE support.notifications SET read_at=coalesce(read_at,now()),updated_at=CASE WHEN read_at IS NULL THEN now() ELSE updated_at END WHERE id=? AND recipient_id=? AND channel='IN_APP'",id,recipient)!=1)throw new ApiException(404,"NOTIFICATION_NOT_FOUND");}
    public void releaseDueInApp(){jdbc.update("UPDATE support.notifications SET status='SENT',sent_at=now(),updated_at=now() WHERE channel='IN_APP' AND status='PENDING' AND scheduled_at<=now()");}
    public Delivery claim(int maxAttempts){return tx.execute(status->{
        var rows=jdbc.query("SELECT id,event_id,email_address,title,body,retry_count FROM support.notifications WHERE channel='EMAIL' AND email_address IS NOT NULL AND email_address<>'' AND scheduled_at<=now() AND retry_count<? AND (status IN ('PENDING','NOT_CONFIGURED') OR (status='FAILED' AND next_retry_at IS NOT NULL AND next_retry_at<=now())) ORDER BY scheduled_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",(rs,i)->new Delivery(rs.getObject("id",UUID.class),rs.getObject("event_id",UUID.class),rs.getString("email_address"),rs.getString("title"),rs.getString("body"),rs.getInt("retry_count")+1),maxAttempts);
        if(rows.isEmpty())return null;var row=rows.getFirst();
        // Persist uncertainty before touching SMTP: a crash must not cause blind retransmission.
        jdbc.update("UPDATE support.notifications SET status='UNKNOWN',retry_count=retry_count+1,next_retry_at=NULL,last_error_code='SMTP_DELIVERY_UNCERTAIN',delivery_started_at=now(),updated_at=now() WHERE id=?",row.id());return row;
    });}
    public void delivered(Delivery row){jdbc.update("UPDATE support.notifications SET status='SENT',sent_at=now(),last_error_code=NULL,next_retry_at=NULL,updated_at=now() WHERE id=? AND status='UNKNOWN'",row.id());}
    public void failed(Delivery row,String error,boolean uncertain,int maxAttempts,int retrySeconds){
        jdbc.update("UPDATE support.notifications SET status=?,last_error_code=?,next_retry_at=?,updated_at=now() WHERE id=? AND status='UNKNOWN'",uncertain?"UNKNOWN":"FAILED",error,!uncertain&&row.attempts()<maxAttempts?Timestamp.from(Instant.now().plusSeconds((long)retrySeconds*row.attempts())):null,row.id());
    }
}
