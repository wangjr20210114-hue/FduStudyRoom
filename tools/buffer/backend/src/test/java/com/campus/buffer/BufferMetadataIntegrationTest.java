package com.campus.buffer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Requires a dedicated database; never points at the application or migration-probe database. */
class BufferMetadataIntegrationTest {
 private final JsonMapper json=JsonMapper.builder().build();
 private final BufferSettings.Member owner=new BufferSettings.Member("metadata-owner",null,"backend",List.of("M01"),false,"");
 private final BufferSettings.Member other=new BufferSettings.Member("metadata-other",null,"backend",List.of("M01"),false,"");
 private final BufferSettings.Member admin=new BufferSettings.Member("metadata-admin",null,"backend",List.of(),true,"");
 private DriverManagerDataSource source;
 private JdbcTemplate db;
 private BufferRepository repository;

 @BeforeEach void dedicatedDatabase(){
  String url=System.getenv("CAMPUS_BUFFER_TEST_DB_URL");
  assertNotNull(url,"CAMPUS_BUFFER_TEST_DB_URL must name a dedicated PostgreSQL campus_buffer_test database (integration tests cannot be skipped)");
  assertTrue(url.matches("jdbc:postgresql://[^/]+/campus_buffer_test(?:\\?.*)?"),"Only the dedicated campus_buffer_test database may be reset");
  source=new DriverManagerDataSource(url,System.getenv("CAMPUS_BUFFER_TEST_DB_USER"),System.getenv("CAMPUS_BUFFER_TEST_DB_PASSWORD"));
  db=new JdbcTemplate(source);
  assertEquals("campus_buffer_test",db.queryForObject("SELECT current_database()",String.class));
  db.execute("DROP SCHEMA IF EXISTS buffer CASCADE");
  repository=new BufferRepository(db);
 }

 private Flyway migrations(String target){
  var config=Flyway.configure().dataSource(source).defaultSchema("buffer").schemas("buffer").locations("classpath:db/migration");
  if(target!=null)config.target(target);
  return config.load();
 }

 private Map<String,Object> metadata(String log){
  Map<String,Object> receipt=new LinkedHashMap<>();
  receipt.put("log",log);
  receipt.put("sha256",BufferSettings.sha(log.getBytes(StandardCharsets.UTF_8)));
  receipt.put("command",List.of("sh","mvnw","test"));
  Map<String,Object> value=new LinkedHashMap<>();
  value.put("module","M01");
  value.put("summary","原始证据往返 🚀");
  value.put("requirements",List.of("IAM-02"));
  value.put("paths",List.of("backend/resource-service/src/main/java/com/campus/identity/Example.java"));
  value.put("evidence",Map.of("requirements",Map.of("IAM-02",Map.of("red",receipt,"green",receipt,"mutation",receipt))));
  return value;
 }

 private String rawLog(){return "w"+(char)0+"s"+(char)0+"l"+(char)0+": 警告\r\n中文日志 🚀\nexpected business assertion\n";}

 @Test void storesOriginalEvidenceAndHashesIdempotently(){
  migrations(null).migrate();
  var metadata=metadata(rawLog());
  String original=json.writeValueAsString(metadata);
  byte[] patch="diff --git a/中文 b/中文\n+🚀\n".getBytes(StandardCharsets.UTF_8);
  UUID id=assertDoesNotThrow(()->repository.insert(owner,metadata,patch),"Valid evidence containing NUL must be accepted without rewriting its log or SHA-256");
  assertEquals(metadata,repository.get(id).get("metadata"),"Every original receipt field must survive retrieval");
  assertEquals(original,json.writeValueAsString(metadata),"Storage encoding must not mutate the submitted receipt map");
  String storedJson=db.queryForObject("SELECT metadata::text FROM buffer.submissions WHERE id=?",String.class,id);
  var stored=json.readValue(storedJson,Map.class);
  var evidence=(Map<?,?>)stored.get("evidence");
  assertEquals(Set.of("storageEncoding","data"),evidence.keySet());
  assertEquals("base64-json-v1",evidence.get("storageEncoding"));
  assertEquals(json.writeValueAsString(metadata.get("evidence")),new String(Base64.getDecoder().decode((String)evidence.get("data")),StandardCharsets.UTF_8),"Stored evidence must encode the exact original JSON UTF-8 bytes");
  assertFalse(storedJson.contains("\\u0000"),"JSONB must contain encoded evidence rather than NUL escape sequences");
  assertEquals("jsonb",db.queryForObject("SELECT pg_typeof(metadata)::text FROM buffer.submissions WHERE id=?",String.class,id));
  assertArrayEquals(patch,repository.patch(id));
  assertEquals(id,repository.insert(owner,metadata,patch),"Retrying the identical upload must return the same submission");
  assertEquals(1,db.queryForObject("SELECT count(*) FROM buffer.submissions",Integer.class));
  assertEquals(BufferSettings.sha((json.writeValueAsString(metadata)+Base64.getEncoder().encodeToString(patch)).getBytes(StandardCharsets.UTF_8)),db.queryForObject("SELECT fingerprint FROM buffer.submissions WHERE id=?",String.class,id));
  @SuppressWarnings("unchecked") var returned=(Map<String,Object>)repository.get(id).get("metadata");
  assertEquals(metadata.get("evidence"),returned.get("evidence"),"Original logs and their supplied hashes cannot be recomputed or normalized");
 }

 @Test void listsAndDispatchesEvidenceWithoutConvertingItToJsonb(){
  migrations(null).migrate();
  var metadata=metadata(rawLog());
  UUID id=assertDoesNotThrow(()->repository.insert(owner,metadata,new byte[]{0,1,2}),"Raw evidence must enter the real queue");
  UUID otherId=repository.insert(other,metadata("other evidence"),new byte[]{3});
  assertEquals(List.of(id.toString()),repository.list(owner).stream().map(row->row.get("id")).toList());
  assertEquals(2,repository.list(admin).size());
  @SuppressWarnings("unchecked") var listed=(Map<String,Object>)repository.list(owner).getFirst().get("metadata");
  assertEquals(Set.of("summary","requirements","paths"),listed.keySet());
  for(String field:listed.keySet())assertEquals(metadata.get(field),listed.get(field));
  var queued=repository.queueSummary(owner);
  assertEquals(1L,((Number)queued.get("pending_count")).longValue());
  assertEquals(0L,((Number)queued.get("ready_count")).longValue());
  String storedJson=db.queryForObject("SELECT metadata::text FROM buffer.submissions WHERE id=?",String.class,id);
  assertEquals(3+storedJson.getBytes(StandardCharsets.UTF_8).length,((Number)queued.get("pending_bytes")).longValue());
  assertTrue(repository.review(id,"reviewer","accepted",true));
  assertEquals(List.of(id),repository.reviewed());
  assertTrue(repository.startVerify(id));
  repository.result(id,"VERIFIED",Map.of("category","success","commit","abc"));
  repository.event(id,"reviewer","VERIFIED",Map.of("category","success"));
  assertTrue(repository.hasReady());
  assertEquals(1,repository.ready().size());
  assertEquals(metadata,repository.ready(2).getFirst().get("metadata"));
  assertEquals(1L,((Number)repository.queueSummary(owner).get("ready_count")).longValue());
  assertEquals("DRAFT",repository.get(otherId).get("state"));
  assertEquals("jsonb",db.queryForObject("SELECT pg_typeof(result)::text FROM buffer.submissions WHERE id=?",String.class,id));
  assertEquals("jsonb",db.queryForObject("SELECT pg_typeof(detail)::text FROM buffer.events WHERE submission_id=?",String.class,id));
 }

 @Test void readsNonemptyVersionThreeDatabaseWithoutChangingExistingSubmissions(){
  migrations("3").migrate();
  var metadata=metadata("legacy 中文 🚀\n");
  byte[] patch="old patch".getBytes(StandardCharsets.UTF_8);
  UUID id=UUID.randomUUID();
  String originalFingerprint=BufferSettings.sha((json.writeValueAsString(metadata)+Base64.getEncoder().encodeToString(patch)).getBytes(StandardCharsets.UTF_8));
  db.update("INSERT INTO buffer.submissions(id,member_id,module_id,layer,fingerprint,metadata,patch) VALUES(?,?,?,?,?,?::jsonb,?)",id,owner.id(),"M01",owner.layer(),originalFingerprint,json.writeValueAsString(metadata),patch);
  repository.result(id,"VERIFIED",Map.of("code","LEGACY"));
  repository.event(id,"reviewer","REVIEW",Map.of("legacy",true));
  var before=repository.get(id);
  String fingerprint=db.queryForObject("SELECT fingerprint FROM buffer.submissions WHERE id=?",String.class,id);
  var history=db.queryForList("SELECT version,checksum FROM buffer.flyway_schema_history WHERE version IN ('1','2','3') ORDER BY version");
  migrations(null).migrate();
  assertEquals(before,repository.get(id),"Existing unencoded evidence, state, result and timestamps must stay unchanged");
  assertEquals(history,db.queryForList("SELECT version,checksum FROM buffer.flyway_schema_history WHERE version IN ('1','2','3') ORDER BY version"),"Published migration history must remain unchanged");
  assertEquals(fingerprint,db.queryForObject("SELECT fingerprint FROM buffer.submissions WHERE id=?",String.class,id));
  assertEquals(id,repository.insert(owner,metadata,patch),"Legacy uploads must remain idempotent after upgrade");
  assertArrayEquals(patch,repository.patch(id));
  assertEquals(1,repository.list(owner).size());
  assertEquals(metadata,repository.ready().getFirst().get("metadata"));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM buffer.events",Integer.class));
  UUID fresh=assertDoesNotThrow(()->repository.insert(owner,metadata(rawLog()),new byte[]{4}),"An upgraded nonempty database must accept original NUL-containing evidence");
  assertEquals(metadata(rawLog()),repository.get(fresh).get("metadata"));
  assertEquals("jsonb",db.queryForObject("SELECT pg_typeof(metadata)::text FROM buffer.submissions WHERE id=?",String.class,fresh));
  assertEquals("3",db.queryForObject("SELECT max(version::int)::text FROM buffer.flyway_schema_history",String.class));
 }
}
