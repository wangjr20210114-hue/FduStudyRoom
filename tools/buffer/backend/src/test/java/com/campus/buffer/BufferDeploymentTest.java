package com.campus.buffer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BufferDeploymentTest {
 @TempDir Path root;
 BufferSettings settings(Map<String,Object> additions)throws Exception {
  Files.createDirectories(root.resolve("spec"));Files.writeString(root.resolve("spec/modules.json"),"{\"modules\":[]}");
  Files.writeString(root.resolve("spec/requirements.json"),"{\"requirements\":[]}");
  Map<String,Object> config=new LinkedHashMap<>();config.put("root",root.toString());config.put("baseline","a".repeat(40));
  config.put("members",List.of(Map.of("id","reviewer","layer","frontend","modules",List.of(),"admin",true,"tokenHash",BufferSettings.sha("test-access".getBytes()))));
  config.putAll(additions);Path p=root.resolve("config.local");Files.writeString(p,JsonMapper.builder().build().writeValueAsString(config));return new BufferSettings(p.toString());
 }
 MockHttpServletResponse submitFrom(String origin,BufferSettings settings)throws Exception {
  var request=new MockHttpServletRequest("POST","/api/sync");request.addHeader("Authorization","Bearer test-access");request.addHeader("Origin",origin);request.setContent("{}".getBytes());
  var response=new MockHttpServletResponse();new AccessFilter(settings).doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(204));return response;
 }
 @Test void migrationsHaveUniqueVersions()throws Exception {
  var resources=new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
  Set<String> versions=new HashSet<>();for(var file:resources)assertTrue(versions.add(file.getFilename().split("__")[0]),"Duplicate migration version: "+file.getFilename());
 }
 @Test void acceptsConfiguredHttpsSite()throws Exception {assertEquals(204,submitFrom("https://gitbuffer.seatraces.com",settings(Map.of("allowedOrigins",List.of("https://gitbuffer.seatraces.com")))).getStatus());}
 @Test void rejectsUnconfiguredSite()throws Exception {assertEquals(403,submitFrom("https://other.example",settings(Map.of("allowedOrigins",List.of("https://gitbuffer.seatraces.com")))).getStatus());}
 @Test void receiveOnlyHostDoesNotStartCi()throws Exception {
  var repository=mock(BufferRepository.class);var id=UUID.randomUUID();when(repository.get(id)).thenReturn(Map.of("state","REVIEWED"));
  var jobs=new BufferJobs(settings(Map.of("verificationEnabled",false)),repository,mock(PlatformTransactionManager.class));
  try {assertEquals("CI_DISABLED_ON_THIS_HOST",assertThrows(GateException.class,()->jobs.verify(id)).code);}finally{jobs.close();}
 }
 @Test void memoryUsesAvailableInsteadOfOnlyFree(){
  var m=SystemResources.parseMemory("MemTotal: 4000000 kB\nMemFree: 100000 kB\nMemAvailable: 1600000 kB\nCached: 1200000 kB\n");
  assertTrue(m.known());assertEquals(4096000000L,m.totalBytes());assertEquals(60.0,m.usedPercent(),0.001);
 }
 @Test void incompleteMemoryIsUnknown(){assertFalse(SystemResources.parseMemory("MemTotal: 4000 kB\nMemFree: 100 kB").known());assertNull(SystemResources.parseMemory("bad data").usedPercent());}
 @Test void scheduledPublicationStillRunsWithoutPressure(){assertEquals(DispatchPolicy.Action.SCHEDULED,DispatchPolicy.decide(true,false,true,true));}
 @Test void pressurePublishesBeforePeriodicDeadline(){assertEquals(DispatchPolicy.Action.PRESSURE_FIRST,DispatchPolicy.decide(true,true,false,true));}
 @Test void pressureCooldownPreventsRepeatedPublication(){assertEquals(DispatchPolicy.Action.NONE,DispatchPolicy.decide(true,true,false,false));}
 @Test void disabledPublicationIsNeverBypassed(){assertEquals(DispatchPolicy.Action.NONE,DispatchPolicy.decide(false,true,true,true));}
}
