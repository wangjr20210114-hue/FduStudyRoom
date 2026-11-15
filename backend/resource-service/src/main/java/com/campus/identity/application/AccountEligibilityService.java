package com.campus.identity.application;
import com.campus.common.error.ApiException;import com.campus.identity.infrastructure.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
public class AccountEligibilityService {
 private final JdbcTemplate jdbc;private final AccountRepository accounts;private final IdentityAuditOutbox audit;
 public AccountEligibilityService(JdbcTemplate jdbc,AccountRepository accounts,IdentityAuditOutbox audit){this.jdbc=jdbc;this.accounts=accounts;this.audit=audit;}
 public record Eligibility(UUID userId,boolean enabled,String role,Set<String> permissions,String departmentCode,boolean canApplyRoom,int authVersion){}
 public Eligibility eligibility(UUID id){var account=accounts.find(id).orElseThrow(()->new ApiException(404,"USER_NOT_FOUND"));boolean enabled=account.enabled()&&account.roleEnabled();var row=jdbc.queryForMap("SELECT department_code,can_apply_room FROM iam.users WHERE id=?",id);return new Eligibility(id,enabled,account.roleCode(),enabled?account.permissions():Set.of(),(String)row.get("department_code"),enabled&&(Boolean)row.get("can_apply_room"),account.authVersion());}
 @Transactional public void update(UUID id,String department,boolean canApply,int expected){
  if(jdbc.queryForList("SELECT id FROM iam.users WHERE id=? FOR UPDATE",UUID.class,id).isEmpty())throw new ApiException(404,"USER_NOT_FOUND");
  var previous=eligibility(id);String code=department==null?null:department.trim();if(code!=null&&code.isEmpty())code=null;if(code!=null&&(code.length()>64||code.chars().anyMatch(Character::isISOControl)))throw new ApiException(400,"INVALID_DEPARTMENT_CODE");
  int changed=jdbc.update("UPDATE iam.users SET department_code=?,can_apply_room=?,auth_version=auth_version+1,updated_at=now() WHERE id=? AND auth_version=?",code,canApply,id,expected);if(changed!=1)throw new ApiException(409,"VERSION_CONFLICT");
  Map<String,Object> before=new LinkedHashMap<>();before.put("authVersion",previous.authVersion());before.put("canApplyRoom",previous.canApplyRoom());before.put("hasDepartment",previous.departmentCode()!=null);if(previous.departmentCode()!=null)before.put("departmentCode",previous.departmentCode());
  Map<String,Object> after=new LinkedHashMap<>();after.put("authVersion",expected+1);after.put("canApplyRoom",canApply);after.put("hasDepartment",code!=null);if(code!=null)after.put("departmentCode",code);audit.enqueue("ELIGIBILITY_CHANGED",id,before,after);
 }
 public record Candidate(UUID userId,String displayName){}
 public List<Candidate> candidates(String permission){if(!Set.of("approval:decide","repair:maintain","repair:assign","repair:accept").contains(permission))throw new ApiException(400,"INVALID_CANDIDATE_PERMISSION");return jdbc.query("SELECT u.id,u.display_name FROM iam.users u JOIN iam.roles r ON r.id=u.role_id JOIN iam.role_permissions rp ON rp.role_id=r.id JOIN iam.permissions p ON p.id=rp.permission_id WHERE u.enabled AND r.enabled AND p.code=? ORDER BY u.display_name,u.id",(rs,n)->new Candidate(rs.getObject(1,UUID.class),rs.getString(2)),permission);}
}
