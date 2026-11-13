package com.campus.buffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;
@Service
public class BufferJobs {
 private final BufferSettings settings;private final BufferRepository repo;private final SystemResources resources;
 private final Set<UUID> pending=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final org.springframework.transaction.support.TransactionTemplate transaction;
 private final JsonMapper json=JsonMapper.builder().build();
 public BufferJobs(BufferSettings settings,BufferRepository repo,org.springframework.transaction.PlatformTransactionManager manager){this(settings,repo,manager,new SystemResources(settings));}
 @org.springframework.beans.factory.annotation.Autowired public BufferJobs(BufferSettings settings,BufferRepository repo,org.springframework.transaction.PlatformTransactionManager manager,SystemResources resources){this.settings=settings;this.repo=repo;this.resources=resources;this.transaction=new org.springframework.transaction.support.TransactionTemplate(manager);}
 @jakarta.annotation.PreDestroy public void close(){worker.shutdownNow();}
 public synchronized void verify(UUID id){
  if(!settings.verificationEnabled())throw new GateException(409,"CI_DISABLED_ON_THIS_HOST");
  if(!resources.canStartCi())throw new GateException(409,"MEMORY_PRESSURE_CI_PAUSED");
  if(!pending.isEmpty())throw new GateException(409,"CI_BUSY");
  String state=(String)repo.get(id).get("state");if(!List.of("REVIEWED","VERIFY_FAILED").contains(state))throw new GateException(409,"STATE_CHANGED");
  if(!pending.add(id))return;
  worker.submit(()->{
   try {
    if(!repo.startVerify(id))return;
    Map<String,Object> input=new LinkedHashMap<>(repo.get(id));input.put("patch",Base64.getEncoder().encodeToString(repo.patch(id)));
    Map<String,Object> result=execute("verify",input,id);
    transaction.executeWithoutResult(status->{repo.result(id,(String)result.getOrDefault("state","VERIFY_FAILED"),result);repo.event(id,"independent-ci","VERIFY",Map.of("state",result.getOrDefault("state","VERIFY_FAILED")));});
   } catch(Exception e){repo.result(id,"VERIFY_FAILED",Map.of("code","CI_UNAVAILABLE_OR_FAILED","category","external"));}
   finally{pending.remove(id);}
  });
 }

 @SuppressWarnings("unchecked") private Map<String,Object> execute(String command,Map<String,Object> input,UUID id)throws Exception {
  Path dir=settings.root().resolve(".campus/buffer/jobs");Files.createDirectories(dir);Path file=dir.resolve(id+"-"+command+".local");
  Files.writeString(file,json.writeValueAsString(input));
  Path outputFile=dir.resolve(id+"-"+command+"-output.local");
  var process=new ProcessBuilder(settings.python(),settings.root().resolve("tools/team/buffer_job.py").toString(),command,"--config",settings.path().toString(),"--input",file.toString()).directory(settings.root().toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(outputFile.toFile()).start();
  if(!process.waitFor(20,TimeUnit.MINUTES)){process.destroyForcibly();throw new IllegalStateException("JOB_TIMEOUT");}
  if(process.exitValue()!=0)throw new IllegalStateException("JOB_FAILED");
  return json.readValue(Files.readAllBytes(outputFile),Map.class);
 }
 @Scheduled(fixedDelay=60000) public void tick(){
  repo.recoverStale();
  if(settings.verificationEnabled() && resources.canStartCi() && pending.isEmpty()){
   var reviewed=repo.reviewed();if(!reviewed.isEmpty())try{verify(reviewed.getFirst());}catch(GateException ignored){}
  }
  var action=DispatchPolicy.decide(settings.enabled(),resources.pressure(resources.memory()),repo.due(),repo.pressureDue());
  if(action!=DispatchPolicy.Action.NONE)transaction.executeWithoutResult(status->syncLocked(action));
 }
 public void sync(){
  var action=resources.pressure(resources.memory())?DispatchPolicy.Action.PRESSURE_FIRST:DispatchPolicy.Action.SCHEDULED;
  transaction.executeWithoutResult(status->syncLocked(action));
 }
 private void syncLocked(DispatchPolicy.Action action){
  if(!settings.enabled())throw new GateException(409,"SYNC_DISABLED_OR_CREDENTIALS_MISSING");
  if(!repo.lock())return;
  if(action==DispatchPolicy.Action.PRESSURE_FIRST && !repo.pressureDue())return;
  // A single eligible FIFO entry per tick bounds memory, including normal 72-hour draining.
  var rows=repo.ready(1);
  if(rows.isEmpty()){if(action==DispatchPolicy.Action.SCHEDULED)repo.syncDone(true,settings.interval());else repo.pressureDone(true,settings.pressureCooldown());return;}
  var row=rows.getFirst();UUID id=UUID.fromString((String)row.get("id"));boolean success=false;
  try{var result=execute("publish",row,id);String state=(String)result.getOrDefault("state","PUSH_FAILED");repo.result(id,state,result);success=state.equals("PUBLISHED");}
  catch(Exception e){repo.result(id,"PUSH_FAILED",Map.of("code","PUSH_FAILED_RETRYABLE"));}
  repo.event(id,"buffer-scheduler","PUBLISH_ATTEMPT",Map.of("trigger",action.name(),"success",success));
  if(action==DispatchPolicy.Action.PRESSURE_FIRST)repo.pressureDone(success,settings.pressureCooldown());
  else if(success && repo.hasReady())repo.draining();else repo.syncDone(success,settings.interval());
 }
}
