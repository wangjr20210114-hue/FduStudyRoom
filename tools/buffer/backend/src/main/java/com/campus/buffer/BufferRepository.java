package com.campus.buffer;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
@Repository
public class BufferRepository {
 private final JdbcTemplate db;private final JsonMapper json=JsonMapper.builder().build();
 public BufferRepository(JdbcTemplate db){this.db=db;}
 public List<Map<String,Object>> list(BufferSettings.Member member){return rows("SELECT id,member_id,module_id,layer,state,jsonb_build_object('summary',metadata->'summary','requirements',metadata->'requirements','paths',metadata->'paths') AS metadata,reviewer,review_note,jsonb_build_object('category',result->'category','pull_request',result->'pull_request','code',result->'code','branch',result->'branch','commit',result->'commit') AS result,created_at FROM buffer.submissions "+(member.admin()?"":"WHERE member_id=? ")+"ORDER BY created_at DESC LIMIT 200",member.admin()?new Object[]{}:new Object[]{member.id()});}
 public Map<String,Object> get(UUID id){var result=rows("SELECT id,member_id,module_id,layer,state,metadata,reviewer,review_note,result,created_at FROM buffer.submissions WHERE id=?",id);if(result.isEmpty())throw new GateException(404,"NOT_FOUND");return result.getFirst();}
 @SuppressWarnings("unchecked")
 private List<Map<String,Object>> rows(String sql,Object... args){return db.query(sql,(rs,n)->{
   Map<String,Object> row=new LinkedHashMap<>();for(String key:List.of("id","member_id","module_id","layer","state","reviewer","review_note"))row.put(key,rs.getString(key));
   row.put("created_at",rs.getTimestamp("created_at").toInstant().toString());
   row.put("metadata",originalMetadata(json.readValue(rs.getString("metadata"),Map.class)));row.put("result",json.readValue(rs.getString("result"),Map.class));return row;
 },args);}
 public UUID insert(BufferSettings.Member member,Map<String,Object> metadata,byte[] patch){
  String fingerprint=BufferSettings.sha((json.writeValueAsString(metadata)+Base64.getEncoder().encodeToString(patch)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  UUID id=UUID.randomUUID();db.update("INSERT INTO buffer.submissions(id,member_id,module_id,layer,fingerprint,metadata,patch) VALUES(?,?,?,?,?,?::jsonb,?) ON CONFLICT(member_id,fingerprint) DO NOTHING",id,member.id(),metadata.get("module"),member.layer(),fingerprint,json.writeValueAsString(storageMetadata(metadata)),patch);
  return db.queryForObject("SELECT id FROM buffer.submissions WHERE member_id=? AND fingerprint=?",UUID.class,member.id(),fingerprint);
 }
 private Map<String,Object> storageMetadata(Map<String,Object> metadata){
  // Encode only the evidence at the storage boundary: jsonb rejects escaped NUL.
  // The fingerprint above and all callers continue to use the original receipts.
  Map<String,Object> stored=new LinkedHashMap<>(metadata);
  if(metadata.containsKey("evidence"))stored.put("evidence",Map.of("storageEncoding","base64-json-v1","data",Base64.getEncoder().encodeToString(json.writeValueAsString(metadata.get("evidence")).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  return stored;
 }
 private Map<String,Object> originalMetadata(Map<String,Object> metadata){
  if(metadata.get("evidence") instanceof Map<?,?> evidence && evidence.size()==2 && "base64-json-v1".equals(evidence.get("storageEncoding")) && evidence.get("data") instanceof String data){
   metadata.put("evidence",json.readValue(new String(Base64.getDecoder().decode(data),java.nio.charset.StandardCharsets.UTF_8),Object.class));
  }
  return metadata;
 }
 public byte[] patch(UUID id){return db.queryForObject("SELECT patch FROM buffer.submissions WHERE id=?",byte[].class,id);}
 public boolean review(UUID id,String reviewer,String note,boolean approved){return db.update("UPDATE buffer.submissions SET state=?,reviewer=?,review_note=?,review_at=now(),updated_at=now() WHERE id=? AND "+(approved?"state='DRAFT'":"state IN ('DRAFT','REVIEWED','VERIFY_FAILED','CONFLICT')"),approved?"REVIEWED":"REJECTED",reviewer,note,id)==1;}
 public boolean startVerify(UUID id){return db.update("UPDATE buffer.submissions SET state='VERIFYING',updated_at=now() WHERE id=? AND state IN ('REVIEWED','VERIFY_FAILED')",id)==1;}
 public void result(UUID id,String state,Map<String,Object> result){db.update("UPDATE buffer.submissions SET state=?,result=result || ?::jsonb,updated_at=now() WHERE id=?",state,json.writeValueAsString(result),id);}
 public List<UUID> reviewed(){return db.queryForList("SELECT id FROM buffer.submissions WHERE state='REVIEWED' ORDER BY created_at",UUID.class);}
 public void recoverStale(){db.update("UPDATE buffer.submissions SET state='REVIEWED',updated_at=now(),result=result || '{\"code\":\"STALE_CI_RETRY\",\"category\":\"external\"}'::jsonb WHERE state='VERIFYING' AND updated_at<now()-interval '25 minutes'");}
 public List<Map<String,Object>> ready(){return ready(1);}
 public List<Map<String,Object>> ready(int limit){return rows("SELECT id,member_id,module_id,layer,state,metadata,reviewer,review_note,result,created_at FROM buffer.submissions WHERE state IN ('VERIFIED','PUSH_FAILED') ORDER BY created_at,id LIMIT ?",limit);}
 public boolean hasReady(){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM buffer.submissions WHERE state IN ('VERIFIED','PUSH_FAILED'))",Boolean.class));}
 public boolean pressureDue(){return Boolean.TRUE.equals(db.queryForObject("SELECT (pressure_next_at IS NULL OR pressure_next_at<=now()) AND (retry_at IS NULL OR retry_at<=now()) FROM buffer.sync_state WHERE id=1",Boolean.class));}
 public void pressureDone(boolean success,int cooldown){db.update("UPDATE buffer.sync_state SET pressure_next_at=now()+(? * interval '1 second'),retry_at=CASE WHEN ? THEN NULL ELSE now()+interval '5 minutes' END,last_run=now(),last_status=?,last_trigger='PRESSURE_FIRST' WHERE id=1",cooldown,success,success?"SUCCESS":"RETRY_PENDING");}
 public void draining(){db.update("UPDATE buffer.sync_state SET next_due=LEAST(next_due,now()),retry_at=NULL,last_run=now(),last_status='DRAINING',last_trigger='SCHEDULED' WHERE id=1");}
 public Map<String,Object> queueSummary(BufferSettings.Member member){return db.queryForMap("SELECT count(*) AS pending_count,coalesce(sum(octet_length(patch)+octet_length(metadata::text)),0)::bigint AS pending_bytes,count(*) FILTER (WHERE state IN ('VERIFIED','PUSH_FAILED')) AS ready_count FROM buffer.submissions WHERE state NOT IN ('PUBLISHED','REJECTED')"+(member.admin()?"":" AND member_id=?"),member.admin()?new Object[]{}:new Object[]{member.id()});}
 public void event(UUID id,String actor,String action,Map<String,Object> detail){db.update("INSERT INTO buffer.events(submission_id,actor,action,detail) VALUES(?,?,?,?::jsonb)",id,actor,action,json.writeValueAsString(detail));}
 public boolean remoteWorkerConnected(){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT FROM pg_stat_activity WHERE datname=current_database() AND usename='fdu_buffer_worker' AND application_name='PostgreSQL JDBC Driver')",Boolean.class));}
 public Map<String,Object> schedule(){return db.queryForMap("SELECT next_due,retry_at,last_run,last_status,pressure_next_at,last_trigger FROM buffer.sync_state WHERE id=1");}
 public boolean due(){return Boolean.TRUE.equals(db.queryForObject("SELECT next_due<=now() AND (retry_at IS NULL OR retry_at<=now()) FROM buffer.sync_state WHERE id=1",Boolean.class));}
 public void syncDone(boolean success,int seconds){if(success)db.update("UPDATE buffer.sync_state SET next_due=now()+(? * interval '1 second'),retry_at=NULL,last_run=now(),last_status='SUCCESS',last_trigger='SCHEDULED' WHERE id=1",seconds);else db.update("UPDATE buffer.sync_state SET retry_at=now()+interval '5 minutes',last_run=now(),last_status='RETRY_PENDING',last_trigger='SCHEDULED' WHERE id=1");}
 public boolean lock(){return Boolean.TRUE.equals(db.queryForObject("SELECT pg_try_advisory_xact_lock(640801902)",Boolean.class));}
}
