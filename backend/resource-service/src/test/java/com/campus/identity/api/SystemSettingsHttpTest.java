package com.campus.identity.api;
import org.junit.jupiter.api.*;import java.util.*;import static org.assertj.core.api.Assertions.*;
class SystemSettingsHttpTest extends IdentityHttpFixture {
 @BeforeEach void fixtureDefaults(){if(jdbc.queryForObject("SELECT to_regclass('iam.system_settings') IS NOT NULL",Boolean.class)){jdbc.update("DELETE FROM iam.system_settings_history");jdbc.update("UPDATE iam.system_settings SET version=1,max_reservation_hours=4,before_start_reminder_minutes=15,after_start_reminder_minutes=10,checkin_deadline_minutes=15,approval_ttl_minutes=60,repair_sla_minutes=120 WHERE id=1");}}
 Map<String,Object> settings(int version){return map("expectedVersion",version,"maxReservationHours",2,"beforeStartReminderMinutes",15,"afterStartReminderMinutes",5,"checkinDeadlineMinutes",15,"approvalTtlMinutes",90,"repairSlaMinutes",180);}
 @Test void defaultAndCurrentSettingsServeServiceKeyOnly()throws Exception{
  assertThat(internal("/internal/system/settings",null).statusCode()).isEqualTo(401);var r=internal("/internal/system/settings",SERVICE);assertThat(r.statusCode()).as("approved default settings readable by trusted services").isEqualTo(200);var t=tree(r);assertThat(t.get("maxReservationHours").asInt()).isEqualTo(4);assertThat(t.get("beforeStartReminderMinutes").asInt()).isEqualTo(15);assertThat(t.get("afterStartReminderMinutes").asInt()).isEqualTo(10);assertThat(t.get("checkinDeadlineMinutes").asInt()).isEqualTo(15);assertThat(t.get("approvalTtlMinutes").asInt()).isEqualTo(60);assertThat(t.get("repairSlaMinutes").asInt()).isEqualTo(120);
 }
 @Test void updateIsVersionedAndHistoryPreservesPreviousValues()throws Exception{
  var r=req(admin,"PUT","/api/admin/settings",settings(1));assertThat(r.statusCode()).as("settings update succeeds atomically").isEqualTo(200);assertThat(tree(r).get("version").asInt()).isEqualTo(2);
  var current=tree(internal("/internal/system/settings",SERVICE));assertThat(current.get("maxReservationHours").asInt()).isEqualTo(2);assertThat(current.get("repairSlaMinutes").asInt()).isEqualTo(180);
  var history=req(admin,"GET","/api/admin/settings/history",null);assertThat(history.statusCode()).isEqualTo(200);var event=tree(history).get(0);assertThat(event.get("actorId").asText()).isNotBlank();assertThat(event.get("changedAt").asText()).isNotBlank();assertThat(event.get("before").get("maxReservationHours").asInt()).isEqualTo(4);assertThat(event.get("after").get("maxReservationHours").asInt()).isEqualTo(2);assertThat(event.get("version").asInt()).isEqualTo(2);
 }
 @Test void invalidTimingDoesNotPartiallyUpdate()throws Exception{
  var before=req(admin,"GET","/api/admin/settings",null);assertThat(before.statusCode()).as("settings administration exists").isEqualTo(200);
  for(String key:List.of("maxReservationHours","approvalTtlMinutes","repairSlaMinutes")){var value=settings(1);value.put(key,0);assertThat(req(admin,"PUT","/api/admin/settings",value).statusCode()).isEqualTo(400);}
  var value=settings(1);value.put("afterStartReminderMinutes",15);assertThat(req(admin,"PUT","/api/admin/settings",value).statusCode()).isEqualTo(400);
  assertThat(tree(req(admin,"GET","/api/admin/settings",null))).isEqualTo(tree(before));assertThat(tree(req(admin,"GET","/api/admin/settings/history",null)).isEmpty()).isTrue();
 }
 @Test void staleAndUnprivilegedSettingsUpdatesAreRejected()throws Exception{
  assertThat(req(admin,"PUT","/api/admin/settings",settings(1)).statusCode()).as("first settings update").isEqualTo(200);
  assertThat(req(admin,"PUT","/api/admin/settings",settings(1)).statusCode()).isEqualTo(409);User u=user();assertThat(req(u.client(),"GET","/api/admin/settings",null).statusCode()).isEqualTo(403);assertThat(req(u.client(),"PUT","/api/admin/settings",settings(2)).statusCode()).isEqualTo(403);assertThat(tree(internal("/internal/system/settings",SERVICE)).get("version").asInt()).isEqualTo(2);
 }
}
