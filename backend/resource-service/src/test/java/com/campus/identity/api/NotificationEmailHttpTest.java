package com.campus.identity.api;
import org.junit.jupiter.api.Test;import java.util.*;import java.time.*;import static org.assertj.core.api.Assertions.*;
class NotificationEmailHttpTest extends IdentityHttpFixture {
 void save(User u,String email)throws Exception{assertThat(req(u.client(),"PATCH","/api/account/email",map("email",email)).statusCode()).as("owner can save notification email").isEqualTo(200);}
 String requestVerification(User u)throws Exception{var r=req(u.client(),"POST","/api/account/email/verification",null);assertThat(r.statusCode()).as("verification attempt returns safe receipt").isIn(200,202);assertThat(posted.get()).as("legacy local notification endpoint receives verification").isNotNull();return posted.get().get("token").asText();}
 @Test void ownerCanSaveEmailWithoutGrantingPrivileges()throws Exception{
  User u=user();save(u,"student1@example.test");var profile=tree(req(u.client(),"GET","/api/account/email",null));assertThat(profile.get("email").asText()).isEqualTo("student1@example.test");assertThat(profile.get("verified").asBoolean()).isFalse();
  assertThat(req(u.client(),"PATCH","/api/account/email",map("email","not-an-email")).statusCode()).isEqualTo(400);assertThat(req(u.client(),"PATCH","/api/account/email",map("email","safe@example.test","role","SYSTEM_ADMIN","departmentCode","CS")).statusCode()).isEqualTo(400);assertThat(jdbc.queryForObject("SELECT can_apply_room FROM iam.users WHERE id=?",Boolean.class,u.id())).isFalse();assertThat(jdbc.queryForObject("SELECT email FROM iam.users WHERE id=?",String.class,u.id())).isEqualTo("student1@example.test");
 }
 @Test void verificationRequestBindsAndDoesNotStoreRawToken()throws Exception{
  User u=user();save(u,"student1@example.test");String token=requestVerification(u);var body=posted.get();assertThat(body.get("userId").asText()).isEqualTo(u.id().toString());assertThat(body.get("email").asText()).isEqualTo("student1@example.test");assertThat(Instant.parse(body.get("expiresAt").asText())).isEqualTo(clock.now.plusSeconds(900));assertThat(body.has("verificationUrl")).isFalse();
  assertThat(jdbc.queryForObject("SELECT row_to_json(c)::text FROM iam.email_verification_challenges c WHERE user_id=?",String.class,u.id())).doesNotContain(token);assertThat(req(u.client(),"GET","/api/account/email",null).body()).doesNotContain(token);assertThat(calls).isEqualTo(1);assertThat(logs.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList()).noneMatch(message->message.contains(token));
 }
 @Test void linkVerifiesOnlyOwnerAndReplaysIdempotently()throws Exception{
  User u=user();save(u,"student1@example.test");String token=requestVerification(u);clock.now=clock.now.plusSeconds(899);
  var first=req(u.client(),"POST","/api/account/email/verify",map("token",token));assertThat(first.statusCode()).isEqualTo(200);assertThat(tree(first).get("verified").asBoolean()).isTrue();var stamp=jdbc.queryForObject("SELECT email_verified_at FROM iam.users WHERE id=?",java.sql.Timestamp.class,u.id());
  assertThat(req(u.client(),"POST","/api/account/email/verify",map("token",token)).statusCode()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT email_verified_at FROM iam.users WHERE id=?",java.sql.Timestamp.class,u.id())).isEqualTo(stamp);
  var address=internal("/internal/identity/users/"+u.id()+"/notification-email",SERVICE);assertThat(address.statusCode()).isEqualTo(200);assertThat(tree(address).get("verified").asBoolean()).isTrue();
 }
 @Test void expiredForeignAndReplacedEmailChallengesAreRejected()throws Exception{
  User u=user();User other=user();save(u,"first@example.test");String token=requestVerification(u);
  assertThat(req(other.client(),"POST","/api/account/email/verify",map("token",token)).statusCode()).isEqualTo(400);
  clock.now=clock.now.plusSeconds(900);assertThat(req(u.client(),"POST","/api/account/email/verify",map("token",token)).statusCode()).isEqualTo(400);
  clock.now=Instant.parse("2030-06-03T00:00:00Z");save(u,"second@example.test");save(u,"first@example.test");assertThat(req(u.client(),"POST","/api/account/email/verify",map("token",token)).statusCode()).isEqualTo(400);assertThat(tree(req(u.client(),"GET","/api/account/email",null)).get("verified").asBoolean()).isFalse();assertThat(logs.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList()).noneMatch(message->message.contains(token));
 }
 @Test void deliveryFailureKeepsSameOperationForSafeRetry()throws Exception{
  User u=user();save(u,"student1@example.test");delivery="NOT_CONFIGURED";String token=requestVerification(u);String operation=posted.get().get("operationId").asText();delivery="ACCEPTED";String again=requestVerification(u);assertThat(again).isEqualTo(token);assertThat(posted.get().get("operationId").asText()).isEqualTo(operation);assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.email_verification_challenges WHERE user_id=?",Integer.class,u.id())).isEqualTo(1);
 }
 @Test void missingOrUnverifiedEmailIsExplicitAndDoesNotBlockLogin()throws Exception{
  User u=user();var own=req(u.client(),"GET","/api/account/email",null);assertThat(own.statusCode()).as("email absence is readable without blocking login").isEqualTo(200);assertThat(tree(own).get("email").isNull()).isTrue();assertThat(tree(own).get("verified").asBoolean()).isFalse();assertThat(req(u.client(),"POST","/api/account/email/verification",null).statusCode()).isEqualTo(400);
  var empty=internal("/internal/identity/users/"+u.id()+"/notification-email",SERVICE);assertThat(empty.statusCode()).isEqualTo(200);assertThat(tree(empty).get("email").isNull()).isTrue();assertThat(tree(empty).get("verified").asBoolean()).isFalse();assertThat(req(login(u.login()),"GET","/api/auth/me",null).statusCode()).isEqualTo(200);
 }
}
