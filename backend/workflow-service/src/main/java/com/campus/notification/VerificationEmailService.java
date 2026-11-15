package com.campus.notification;
import com.campus.common.error.ApiException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** The sensitive request lives only on this synchronous call stack. */
@Service
public class VerificationEmailService {
 public record Receipt(UUID operationId,String status,boolean duplicate,boolean retryable,Instant nextRetryAt,String errorCode){}
 private record Claim(Receipt receipt,boolean send,int attempt){}
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final SmtpTransport smtp;private final Environment env;private final JsonMapper json;
 public VerificationEmailService(JdbcTemplate jdbc,PlatformTransactionManager manager,SmtpTransport smtp,Environment env,JsonMapper json){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.smtp=smtp;this.env=env;this.json=json;}
 private String fingerprint(VerificationEmailRequest request){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(List.of(request.operationId().toString(),request.userId().toString(),request.email(),request.token(),request.expiresAt().toString()))));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("Digest unavailable");}}
 private String origin(){
  String configured=env.getProperty("campus.notifications.verification-origin","https://studyroom.seatraces.com");
  try{URI uri=URI.create(configured);boolean secure="https".equals(uri.getScheme());boolean local="http".equals(uri.getScheme())&&uri.getHost()!=null&&Set.of("127.0.0.1","localhost","[::1]").contains(uri.getHost())&&env.getProperty("campus.notifications.verification-allow-insecure",Boolean.class,false);
   if(!(secure||local)||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))return null;
   return configured.replaceAll("/+$","");
  }catch(IllegalArgumentException invalid){return null;}
 }
 private Receipt read(UUID id,boolean duplicate){return jdbc.queryForObject("SELECT status,retryable,next_retry_at,error_code FROM support.verification_email_receipts WHERE operation_id=?",(r,n)->new Receipt(id,r.getString("status"),duplicate,r.getBoolean("retryable"),r.getTimestamp("next_retry_at")==null?null:r.getTimestamp("next_retry_at").toInstant(),r.getString("error_code")),id);}
 private void finish(UUID id,String state,boolean retryable,String code){
  Instant next=retryable?Instant.now().plusSeconds(Math.max(1,Math.min(86400,env.getProperty("campus.notifications.retry-seconds",Integer.class,30)))):null;
  jdbc.update("UPDATE support.verification_email_receipts SET status=?,retryable=?,next_retry_at=?,error_code=?,updated_at=now() WHERE operation_id=?",state,retryable,next==null?null:Timestamp.from(next),code,id);
 }
 public Receipt deliver(VerificationEmailRequest request){
  String digest=fingerprint(request);String callback=origin();Instant now=Instant.now();
  Claim claim=tx.execute(transaction->{
   jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",request.operationId().toString());
   var old=jdbc.queryForList("SELECT payload_hash,attempts FROM support.verification_email_receipts WHERE operation_id=?",request.operationId());boolean duplicate=!old.isEmpty();int attempts=0;
   if(duplicate){
    String saved=(String)old.getFirst().get("payload_hash");if(!MessageDigest.isEqual(saved.getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII)))throw new ApiException(409,"VERIFICATION_OPERATION_CONFLICT");
    Receipt current=read(request.operationId(),true);attempts=((Number)old.getFirst().get("attempts")).intValue();
    if(Set.of("ACCEPTED","UNKNOWN").contains(current.status())||!current.retryable())return new Claim(current,false,attempts);
    if(!now.isBefore(request.expiresAt())){finish(request.operationId(),"FAILED",false,"VERIFICATION_EXPIRED");return new Claim(read(request.operationId(),true),false,attempts);}
    if(current.nextRetryAt()!=null&&now.isBefore(current.nextRetryAt()))return new Claim(current,false,attempts);
   }else jdbc.update("INSERT INTO support.verification_email_receipts(operation_id,payload_hash,status,attempts,retryable) VALUES(?,?,'UNKNOWN',0,false)",request.operationId(),digest);
   if(!now.isBefore(request.expiresAt())){finish(request.operationId(),"FAILED",false,"VERIFICATION_EXPIRED");return new Claim(read(request.operationId(),duplicate),false,attempts);}
   if(callback==null||!smtp.configured()){finish(request.operationId(),"NOT_CONFIGURED",true,callback==null?"VERIFICATION_ORIGIN_NOT_CONFIGURED":"SMTP_NOT_CONFIGURED");return new Claim(read(request.operationId(),duplicate),false,attempts);}
   // Committed before SMTP. A process crash or lost HTTP acknowledgement leaves UNKNOWN.
   jdbc.update("UPDATE support.verification_email_receipts SET status='UNKNOWN',retryable=false,next_retry_at=NULL,error_code='SMTP_DELIVERY_UNCERTAIN',attempts=attempts+1,updated_at=now() WHERE operation_id=?",request.operationId());
   return new Claim(read(request.operationId(),duplicate),true,attempts+1);
  });
  if(!claim.send())return claim.receipt();
  String link=callback+"/account#verify-email="+URLEncoder.encode(request.token(),StandardCharsets.UTF_8);
  String body="请登录本人账号并验证当前通知邮箱：\n"+link+"\n有效期至 "+request.expiresAt()+"\n\nPlease sign in to your own account to verify your current notification email:\n"+link+"\nExpires at "+request.expiresAt();
  try{
   smtp.send(new NotificationStore.Delivery(request.operationId(),request.operationId(),request.email(),"验证通知邮箱 / Verify notification email",body,claim.attempt()));
   finish(request.operationId(),"ACCEPTED",false,null);
  }catch(SmtpTransport.DeliveryFailure failure){
   if(failure.uncertain())finish(request.operationId(),"UNKNOWN",false,failure.code());
   else finish(request.operationId(),"SMTP_NOT_CONFIGURED".equals(failure.code())?"NOT_CONFIGURED":"FAILED",true,failure.code());
  }
  return read(request.operationId(),claim.receipt().duplicate());
 }
}
