package com.campus.notification;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import static org.junit.jupiter.api.Assertions.*;

class VerificationEmailIntegrationTest extends SupportHttpFixture {
 Map<String,Object> input(){return new LinkedHashMap<>(Map.of("operationId",UUID.randomUUID(),"userId",user,"email","verify-"+UUID.randomUUID()+"@example.test","token","verification-token-"+UUID.randomUUID(),"expiresAt",Instant.now().plusSeconds(900).toString()));}
 tools.jackson.databind.JsonNode send(Map<String,Object> e)throws Exception{return require(request("POST","/internal/notifications/verification-email",e,null,KEY),200);}
 void due(Map<String,Object> e){jdbc.update("UPDATE support.verification_email_receipts SET next_retry_at=now()-interval '1 second' WHERE operation_id=?",e.get("operationId"));}
 @Test void realMailpitAndDuplicateReceipt()throws Exception {
  var e=input();var r=send(e);assertEquals("ACCEPTED",r.path("status").asText());assertFalse(r.path("retryable").asBoolean());assertEquals(1,mailCount(e.get("email").toString()));
  var mail=mail(e.get("email").toString());assertTrue(mail.path("Text").asText().contains("https://studyroom.seatraces.com/account#verify-email="+e.get("token")));assertTrue(mail.path("Text").asText().contains("验证"));assertTrue(mail.path("Text").asText().contains("verify"));
  var again=send(e);assertEquals("ACCEPTED",again.path("status").asText());assertTrue(again.path("duplicate").asBoolean());assertEquals(1,mailCount(e.get("email").toString()));
 }
 @Test void definiteFailureRetriesOnlyAfterBackoff()throws Exception {
  var e=input();try(var smtp=new NotificationIntegrationTest.FaultSmtp(false)){overrides(Map.of("campus.notifications.smtp.host","127.0.0.1","campus.notifications.smtp.port",smtp.port()));var r=send(e);assertEquals("FAILED",r.path("status").asText());assertTrue(r.path("retryable").asBoolean());assertFalse(r.path("nextRetryAt").isNull());}
  send(e);assertEquals(0,mailCount(e.get("email").toString()));app.getEnvironment().getPropertySources().remove("support-overrides");due(e);assertEquals("ACCEPTED",send(e).path("status").asText());assertEquals(1,mailCount(e.get("email").toString()));
 }
 @Test void uncertainAndPersistedUnknownNeverResend()throws Exception {
  var e=input();try(var smtp=new NotificationIntegrationTest.FaultSmtp(true)){overrides(Map.of("campus.notifications.smtp.host","127.0.0.1","campus.notifications.smtp.port",smtp.port()));var r=require(request("POST","/internal/notifications/verification-email",e,null,KEY),202);assertEquals("UNKNOWN",r.path("status").asText());assertTrue(smtp.received.get());}
  app.getEnvironment().getPropertySources().remove("support-overrides");var retry=require(request("POST","/internal/notifications/verification-email",e,null,KEY),202);assertEquals("UNKNOWN",retry.path("status").asText());assertFalse(retry.path("retryable").asBoolean());assertEquals(0,mailCount(e.get("email").toString()));
  var other=input();overrides(Map.of("campus.notifications.smtp.host",""));send(other);jdbc.update("UPDATE support.verification_email_receipts SET status='UNKNOWN',retryable=false,next_retry_at=NULL WHERE operation_id=?",other.get("operationId"));app.getEnvironment().getPropertySources().remove("support-overrides");require(request("POST","/internal/notifications/verification-email",other,null,KEY),202);assertEquals(0,mailCount(other.get("email").toString()));
 }
 @Test void storesOnlyFingerprintAndSafeReceipt(CapturedOutput output)throws Exception {
  var e=input();send(e);String record=jdbc.queryForObject("SELECT row_to_json(r)::text FROM support.verification_email_receipts r WHERE operation_id=?",String.class,e.get("operationId"));for(String value:List.of(e.get("token").toString(),e.get("email").toString(),"#verify-email=")){assertFalse(record.contains(value));assertFalse(output.getAll().contains(value));}
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE event_id=?",Integer.class,e.get("operationId")));
  e.put("token","different-verification-token-"+UUID.randomUUID());require(request("POST","/internal/notifications/verification-email",e,null,KEY),409);
 }
 @Test void serviceAuthAndDtoBoundaries()throws Exception {
  var e=input();send(e);require(request("POST","/internal/notifications/verification-email",input(),cookie(user),null),401);require(request("POST","/internal/notifications/verification-email",input(),null,"invalid"),401);
  for(var field:Map.of("email","bad\r\nBcc: other@example.test","token","short","userId","not-a-uuid","expiresAt","not-an-instant").entrySet()){var bad=input();bad.put(field.getKey(),field.getValue());require(request("POST","/internal/notifications/verification-email",bad,null,KEY),400);}
  var bad=input();bad.put("url","https://evil.example");require(request("POST","/internal/notifications/verification-email",bad,null,KEY),400);
 }
 @Test void configurationExpiryAndConcurrentReplay()throws Exception {
  var e=input();overrides(Map.of("campus.notifications.smtp.host",""));var r=send(e);assertEquals("NOT_CONFIGURED",r.path("status").asText());assertTrue(r.path("retryable").asBoolean());assertEquals(0,mailCount(e.get("email").toString()));
  var expired=input();expired.put("expiresAt",Instant.now().minusSeconds(1).toString());assertEquals("FAILED",send(expired).path("status").asText());assertFalse(send(expired).path("retryable").asBoolean());app.getEnvironment().getPropertySources().remove("support-overrides");
  var parallel=input();try(var pool=Executors.newFixedThreadPool(3)){var tasks=new ArrayList<Callable<Integer>>();for(int i=0;i<6;i++)tasks.add(()->request("POST","/internal/notifications/verification-email",parallel,null,KEY).statusCode());for(var f:pool.invokeAll(tasks))assertTrue(Set.of(200,202).contains(f.get()));}assertEquals("ACCEPTED",send(parallel).path("status").asText());assertEquals(1,mailCount(parallel.get("email").toString()));
 }
}
