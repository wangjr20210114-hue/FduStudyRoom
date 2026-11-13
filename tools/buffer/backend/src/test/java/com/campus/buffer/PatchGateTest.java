package com.campus.buffer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class PatchGateTest {
 @TempDir Path root;
 BufferSettings settings;PatchGate gate;BufferSettings.Member member;
 @BeforeEach void setup()throws Exception {
  Files.createDirectories(root.resolve("spec"));var mapper=JsonMapper.builder().build();
  var module=Map.of("id","M02","write_paths",Map.of("frontend",List.of("frontend/src/modules/resource/")));
  Files.writeString(root.resolve("spec/modules.json"),mapper.writeValueAsString(Map.of("modules",List.of(module))));
  Files.writeString(root.resolve("spec/requirements.json"),"{\"requirements\":[]}");
  var configured=Map.of("id","frontend-a","layer","frontend","modules",List.of("M02"),"tokenHash",BufferSettings.sha("secret-test-token".getBytes()));
  Path config=root.resolve("config.local");Files.writeString(config,mapper.writeValueAsString(Map.of("root",root.toString(),"baseline","a".repeat(40),"members",List.of(configured),"intervalSeconds",259200)));
  settings=new BufferSettings(config.toString());gate=new PatchGate(settings);member=settings.authenticate("Bearer secret-test-token");
 }
 byte[] patch(String path){return ("diff --git a/"+path+" b/"+path+"\nnew file mode 100644\nindex 0000000..7898192\n--- /dev/null\n+++ b/"+path+"\n@@ -0,0 +1 @@\n+a\n").getBytes(StandardCharsets.UTF_8);}
 @Test void acceptsOnlyOwnedPaths(){assertEquals(List.of("frontend/src/modules/resource/page.vue"),gate.check(member,"M02",patch("frontend/src/modules/resource/page.vue")));}
 @Test void rejectsOtherModulesAndSharedFiles(){for(String path:List.of("frontend/src/modules/repair/page.vue","frontend/src/shared/styles/base.css","frontend/src/modules/resource/.env","frontend/src/modules/resource/a.local"))assertThrows(GateException.class,()->gate.check(member,"M02",patch(path)));}
 @Test void rejectsUnassignedModule(){assertEquals(403,assertThrows(GateException.class,()->gate.check(member,"M04",patch("frontend/src/modules/resource/page.vue"))).status);}
 @Test void rejectsSymlinksAndRenameBypass(){assertThrows(GateException.class,()->gate.check(member,"M02",new String(patch("frontend/src/modules/resource/link"),StandardCharsets.UTF_8).replace("100644","120000").getBytes(StandardCharsets.UTF_8)));assertThrows(GateException.class,()->gate.check(member,"M02","diff --git a/AGENTS.md b/frontend/src/modules/resource/page.vue\nsimilarity index 100%\nrename from AGENTS.md\nrename to frontend/src/modules/resource/page.vue\n".getBytes(StandardCharsets.UTF_8)));}
 @Test void duplicateModuleLayerOwnersAreRejected()throws Exception {
  var mapper=JsonMapper.builder().build();Path file=root.resolve("config.local");
  @SuppressWarnings("unchecked") Map<String,Object> config=mapper.readValue(Files.readString(file),Map.class);
  var other=Map.of("id","frontend-b","layer","frontend","modules",List.of("M02"),"tokenHash",BufferSettings.sha("second-test-token".getBytes()));
  config.put("members",List.of(Map.of("id","frontend-a","layer","frontend","modules",List.of("M02"),"tokenHash",BufferSettings.sha("secret-test-token".getBytes())),other));
  Files.writeString(file,mapper.writeValueAsString(config));
  assertEquals("DUPLICATE_MODULE_LAYER_OWNER",assertThrows(IllegalArgumentException.class,()->new BufferSettings(file.toString())).getMessage());
 }
 @Test void identityIsNotAuthentication(){assertNull(settings.authenticate("Bearer frontend-a"));assertNull(settings.authenticate(null));assertFalse(member.publicView().containsKey("tokenHash"));assertEquals("frontend-a",member.id());}
}
