package com.campus.identity.api;
import org.junit.jupiter.api.Test;import java.util.*;import static org.assertj.core.api.Assertions.*;
class AccountEligibilityHttpTest extends IdentityHttpFixture {
 @Test void adminGrantVersionRevokesOldSession()throws Exception{
  User u=user();var r=req(admin,"PATCH","/api/admin/users/"+u.id()+"/eligibility",map("departmentCode","CS","canApplyRoom",true,"expectedAuthVersion",1));
  assertThat(r.statusCode()).as("administrator grants verified eligibility").isEqualTo(204);
  assertThat(req(u.client(),"GET","/api/auth/me",null).statusCode()).as("old session revoked").isEqualTo(401);
  var e=internal("/internal/identity/users/"+u.id()+"/eligibility",SERVICE);assertThat(e.statusCode()).isEqualTo(200);assertThat(tree(e).get("departmentCode").asText()).isEqualTo("CS");assertThat(tree(e).get("canApplyRoom").asBoolean()).isTrue();assertThat(tree(e).get("authVersion").asInt()).isEqualTo(2);
 }
 @Test void staleQualificationDoesNotOverwrite()throws Exception{
  User u=user();String path="/api/admin/users/"+u.id()+"/eligibility";
  assertThat(req(admin,"PATCH",path,map("departmentCode","CS","canApplyRoom",true,"expectedAuthVersion",1)).statusCode()).as("first eligibility update").isEqualTo(204);
  assertThat(req(admin,"PATCH",path,map("departmentCode","OTHER","canApplyRoom",false,"expectedAuthVersion",1)).statusCode()).isEqualTo(409);
  assertThat(jdbc.queryForObject("SELECT department_code FROM iam.users WHERE id=?",String.class,u.id())).isEqualTo("CS");
 }
 @Test void ordinaryCannotSetEligibility()throws Exception{
  User u=user();assertThat(req(u.client(),"PATCH","/api/admin/users/"+u.id()+"/eligibility",map("departmentCode","CS","canApplyRoom",true,"expectedAuthVersion",1)).statusCode()).isEqualTo(403);
  assertThat(req(fresh(),"POST","/api/auth/register",map("loginName","forged-"+UUID.randomUUID().toString().substring(0,8),"password",PASSWORD,"departmentCode","CS","canApplyRoom",true)).statusCode()).isEqualTo(400);
  assertThat(jdbc.queryForObject("SELECT can_apply_room FROM iam.users WHERE id=?",Boolean.class,u.id())).isFalse();
 }
 @Test void disabledUsersPreserveHistoryAndRejectOldSessions()throws Exception{
  User u=user();UUID role=jdbc.queryForObject("SELECT role_id FROM iam.users WHERE id=?",UUID.class,u.id());
  jdbc.execute("CREATE TABLE IF NOT EXISTS iam.account_reference_fixture(id uuid PRIMARY KEY,user_id uuid REFERENCES iam.users(id))");UUID ref=UUID.randomUUID();jdbc.update("INSERT INTO iam.account_reference_fixture VALUES(?,?)",ref,u.id());
  assertThat(req(admin,"PATCH","/api/admin/users/"+u.id()+"/access",map("roleId",role,"enabled",false,"expectedAuthVersion",1)).statusCode()).isEqualTo(204);
  assertThat(req(u.client(),"GET","/api/auth/me",null).statusCode()).isEqualTo(401);assertThat(req(fresh(),"POST","/api/auth/login",map("loginName",u.login(),"password",PASSWORD)).statusCode()).isEqualTo(401);
  assertThat(jdbc.queryForObject("SELECT user_id FROM iam.account_reference_fixture WHERE id=?",UUID.class,ref)).isEqualTo(u.id());
 }
 @Test void serviceKeyRequiredAndDisabledRoleHasNoEffectivePermissions()throws Exception{
  User u=user();String p="/internal/identity/users/"+u.id()+"/eligibility";
  assertThat(internal(p,null).statusCode()).isEqualTo(401);assertThat(internal(p,"bad").statusCode()).isEqualTo(401);var good=internal(p,SERVICE);assertThat(good.statusCode()).as("trusted eligibility endpoint").isEqualTo(200);assertThat(tree(good).get("userId").asText()).isEqualTo(u.id().toString());
  UUID role=UUID.randomUUID();jdbc.update("INSERT INTO iam.roles(id,code,name,enabled) VALUES(?,?,?,false)",role,"DISABLED_"+role.toString().replace("-",""),"Disabled");jdbc.update("UPDATE iam.users SET role_id=? WHERE id=?",role,u.id());
  var disabled=tree(internal(p,SERVICE));assertThat(disabled.get("enabled").asBoolean()).isFalse();assertThat(disabled.get("permissions").isEmpty()).isTrue();
 }
 @Test void defaultPermissionsCoverNormalFlows()throws Exception{
  User u=user();var permissions=tree(req(u.client(),"GET","/api/auth/me",null)).get("permissions");Set<String> codes=new HashSet<>();permissions.forEach(p->codes.add(p.asText()));
  assertThat(codes).as("normal users retain normal-flow permissions").contains("resource:rooms:read","reservation:create","reservation:cancel","reservation:checkin","approval:apply","repair:report","notification:read").doesNotContain("repair:assign","audit:read","statistics:read","reservation:admin-cancel","reservation:admin-read","approval:flow:read","approval:flow:write","approval:admin-cancel");
  Set<String> administrator=new HashSet<>();tree(req(admin,"GET","/api/auth/me",null)).get("permissions").forEach(p->administrator.add(p.asText()));assertThat(administrator).as("administrator has independently gated reservation and approval administration").contains("reservation:admin-read","approval:flow:read","approval:flow:write","approval:admin-cancel");
 }

 @Test void trustedDirectoryOnlyListsEffectivePermissionCandidates()throws Exception{
  User u=user();UUID role=UUID.randomUUID();jdbc.update("INSERT INTO iam.roles(id,code,name) VALUES(?,?,?)",role,"WORK_"+role.toString().replace("-",""),"Custom worker");jdbc.update("INSERT INTO iam.role_permissions(role_id,permission_id) SELECT ?,id FROM iam.permissions WHERE code='repair:maintain'",role);jdbc.update("UPDATE iam.users SET role_id=? WHERE id=?",role,u.id());
  var r=internal("/internal/identity/users?permission=repair:maintain",SERVICE);assertThat(r.statusCode()).as("trusted candidate directory exists").isEqualTo(200);assertThat(r.body()).contains(u.id().toString()).contains(u.login());
  jdbc.update("UPDATE iam.roles SET enabled=false WHERE id=?",role);assertThat(internal("/internal/identity/users?permission=repair:maintain",SERVICE).body()).doesNotContain(u.id().toString());assertThat(internal("/internal/identity/users?permission=identity:read",SERVICE).statusCode()).isEqualTo(400);assertThat(internal("/internal/identity/users?permission=approval:decide",null).statusCode()).isEqualTo(401);
  Map<String,UUID> expected=new HashMap<>();
  for(String permission:List.of("repair:assign","repair:accept","approval:decide")){User candidate=user();UUID candidateRole=UUID.randomUUID();jdbc.update("INSERT INTO iam.roles(id,code,name) VALUES(?,?,?)",candidateRole,"CAND_"+candidateRole.toString().replace("-","").toUpperCase(Locale.ROOT),"Permission-specific candidate");jdbc.update("INSERT INTO iam.role_permissions(role_id,permission_id) SELECT ?,id FROM iam.permissions WHERE code=?",candidateRole,permission);jdbc.update("UPDATE iam.users SET role_id=? WHERE id=?",candidateRole,candidate.id());expected.put(permission,candidate.id());}
  for(String permission:List.of("repair:assign","repair:accept")){var response=internal("/internal/identity/users?permission="+permission,SERVICE);assertThat(response.statusCode()).as("trusted repair administration candidates use their own permission").isEqualTo(200);Set<String> actual=new HashSet<>();tree(response).forEach(row->actual.add(row.get("userId").asText()));assertThat(actual).contains(expected.get(permission).toString()).doesNotContain(expected.get("approval:decide").toString());String other=permission.equals("repair:assign")?"repair:accept":"repair:assign";assertThat(actual).doesNotContain(expected.get(other).toString());}

 }
 @Test void roleChangesAndDeniedRequestsProduceSafeAuditOutbox()throws Exception{
  String code="AUDIT_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);var r=req(admin,"POST","/api/admin/roles",map("code",code,"name","Audit fixture","permissions",List.of()));assertThat(r.statusCode()).isEqualTo(201);
  assertThat(jdbc.queryForObject("SELECT to_regclass('iam.audit_outbox') IS NOT NULL",Boolean.class)).as("role mutation is durably audited").isTrue();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.audit_outbox WHERE action='ROLE_CREATED' AND source_id=?",Integer.class,UUID.fromString(tree(r).get("id").asText()))).isPositive();
  User u=user();assertThat(req(u.client(),"POST","/api/admin/roles",map("code","DENIED_"+code,"name","Denied","permissions",List.of())).statusCode()).isEqualTo(403);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.audit_outbox WHERE actor_id=? AND action='ACCESS_DENIED'",Integer.class,u.id())).isPositive();
  String payloads=String.join("",jdbc.queryForList("SELECT payload::text FROM iam.audit_outbox",String.class));assertThat(payloads).doesNotContain(PASSWORD).doesNotContain("student1@example.test").doesNotContain("FDU_SESSION");
 }
 @Test void eligibilityAndAuditAreAtomic()throws Exception{
  User u=user();assertThat(req(admin,"PATCH","/api/admin/users/"+u.id()+"/eligibility",map("departmentCode","CS","canApplyRoom",true,"expectedAuthVersion",1)).statusCode()).as("qualification first stored with audit").isEqualTo(204);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.audit_outbox WHERE source_id=? AND action='ELIGIBILITY_CHANGED'",Integer.class,u.id())).isPositive();
  jdbc.execute("ALTER TABLE iam.audit_outbox ADD CONSTRAINT identity_fixture_audit_failure CHECK(action<>'ELIGIBILITY_CHANGED') NOT VALID");
  try{assertThat(req(admin,"PATCH","/api/admin/users/"+u.id()+"/eligibility",map("departmentCode","OTHER","canApplyRoom",false,"expectedAuthVersion",2)).statusCode()).isGreaterThanOrEqualTo(400);assertThat(jdbc.queryForObject("SELECT auth_version FROM iam.users WHERE id=?",Integer.class,u.id())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT department_code FROM iam.users WHERE id=?",String.class,u.id())).isEqualTo("CS");}finally{jdbc.execute("ALTER TABLE iam.audit_outbox DROP CONSTRAINT identity_fixture_audit_failure");}
 }
}
