package com.campus.buffer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
@RestController
public class BufferController implements WebMvcConfigurer {
 private final BufferSettings settings;private final BufferRepository repo;private final BufferJobs jobs;private final PatchGate gate;private final SystemResources resources;
 public BufferController(BufferSettings settings,BufferRepository repo,BufferJobs jobs,PatchGate gate,SystemResources resources){this.settings=settings;this.repo=repo;this.jobs=jobs;this.gate=gate;this.resources=resources;}
 @Override public void addResourceHandlers(ResourceHandlerRegistry registry){registry.addResourceHandler("/**").addResourceLocations(settings.root().resolve("tools/buffer/frontend/dist").toUri().toString());}
 private BufferSettings.Member member(HttpServletRequest request){return (BufferSettings.Member)request.getAttribute("member");}
 private BufferSettings.Member admin(HttpServletRequest request){var member=member(request);if(!member.admin())throw new GateException(403,"ADMIN_REQUIRED");return member;}
 @GetMapping("/") public org.springframework.web.servlet.ModelAndView home(){return new org.springframework.web.servlet.ModelAndView("forward:/index.html");}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP");}
 @GetMapping("/api/me") public Map<String,Object> me(HttpServletRequest request){var result=new LinkedHashMap<>(member(request).publicView());result.put("baseline",settings.baseline());return result;}
 @GetMapping("/api/status") public Map<String,Object> status(HttpServletRequest request){var result=new LinkedHashMap<>(resources.snapshot());result.put("remoteWorkerConnected",repo.remoteWorkerConnected());result.put("queue",repo.queueSummary(member(request)));return result;}
 @GetMapping("/api/submissions") public List<Map<String,Object>> list(HttpServletRequest request){return repo.list(member(request));}
 @GetMapping("/api/requirements") @SuppressWarnings("unchecked") public List<Map<String,Object>> requirements(HttpServletRequest request){
  var member=member(request);var submissions=repo.list(member);
  return settings.requirements().stream().filter(q->member.admin() || (!Set.of("coordinator","human").contains(q.getOrDefault("execution_owner","member")) && member.modules().contains(q.get("module")) && ((List<String>)q.get("layers")).stream().anyMatch(member.layers()::contains))).map(q->{
   Map<String,Object> copy=new LinkedHashMap<>(q);
   var layers=(List<String>)q.get("layers");var baseline=(Map<String,String>)q.get("baseline_status");
   Map<String,String> states=new LinkedHashMap<>();for(String layer:layers)states.put(layer,baseline.getOrDefault(layer,"pending"));
   Set<String> found=new HashSet<>();
   for(var row:submissions){var meta=(Map<String,Object>)row.get("metadata");String layer=(String)row.get("layer");
    if(layers.contains(layer)&&((List<String>)meta.get("requirements")).contains(q.get("id"))&&found.add(layer))states.put(layer,switch((String)row.get("state")){case "VERIFIED","PUBLISHED"->"verified";case "DRAFT","REVIEWED","VERIFYING"->"awaiting_review_or_ci";case "REJECTED"->baseline.getOrDefault(layer,"pending");case "VERIFY_FAILED"->"external".equals(((Map<String,Object>)row.get("result")).get("category"))?"blocked_external":"needs_fix";case "CONFLICT"->"conflict_human";case "PUSH_FAILED"->"publication_retry";default->"needs_attention";});
   }
   String state=(member.admin()||"fullstack".equals(member.layer()))?(states.values().stream().allMatch("accepted"::equals)?"accepted":states.values().stream().allMatch(v->v.equals("accepted")||v.equals("verified"))?"verified":"pending"):states.getOrDefault(member.layer(),"pending");
   copy.put("state",state);copy.put("layer_states",states);copy.put("skip_coding",!Set.of("pending","needs_fix").contains(state));return copy;
  }).toList();
 }
 @org.springframework.transaction.annotation.Transactional @PostMapping("/api/submissions") @SuppressWarnings("unchecked") public Map<String,Object> submit(@RequestBody Map<String,Object> input,HttpServletRequest request){
  var member=member(request);if(member.admin())throw new GateException(403,"USE_MEMBER_IDENTITY");
  String module=Objects.toString(input.get("module"),"");String base=Objects.toString(input.get("base"),"");
  if(!base.equals(settings.baseline()))throw new GateException(409,"BASELINE_CHANGED");
  String summary=Objects.toString(input.get("summary"),"");if(summary.isBlank()||summary.length()>200||summary.chars().anyMatch(c->c<32))throw new GateException(400,"SUMMARY_REQUIRED");
  var ids=(List<String>)input.get("requirements");if(ids==null||ids.isEmpty()||ids.size()>80)throw new GateException(400,"REQUIREMENTS_REQUIRED");
  for(String id:ids)if(settings.requirements().stream().noneMatch(q->!Set.of("coordinator","human").contains(q.getOrDefault("execution_owner","member"))&&q.get("id").equals(id)&&q.get("module").equals(module)&&((List<String>)q.get("layers")).contains(member.layer())))throw new GateException(403,"REQUIREMENT_SCOPE_MISMATCH");
  for(String id:ids)if(settings.requirements().stream().anyMatch(q->q.get("id").equals(id)&&"accepted".equals(((Map<String,String>)q.get("baseline_status")).get(member.layer()))))throw new GateException(409,"REQUIREMENT_ALREADY_ACCEPTED");
  byte[] patch;try{patch=Base64.getDecoder().decode((String)input.get("patch"));}catch(Exception e){throw new GateException(400,"PATCH_INVALID");}
  var paths=gate.check(member,module,patch);var evidence=(Map<String,Object>)input.get("evidence");
  checkEvidence(evidence,ids);
  String commitType=Objects.toString(input.getOrDefault("commit_type","feat"));
   var details=(List<String>)input.getOrDefault("details",List.of(summary));
   if(!Set.of("feat","fix","refactor").contains(commitType)||details.isEmpty()||details.size()>30||details.stream().anyMatch(x->x==null||x.isBlank()||x.length()>500||x.chars().anyMatch(c->c<32)))throw new GateException(400,"INVALID_COMMIT_DESCRIPTION");
   var meta=new LinkedHashMap<String,Object>();meta.put("commit_type",commitType);meta.put("details",details);meta.put("module",module);meta.put("base",base);meta.put("requirements",ids);meta.put("summary",summary);meta.put("evidence",evidence);meta.put("paths",paths);
  UUID id=repo.insert(member,meta,patch);repo.event(id,member.id(),"UPLOAD_DRAFT",Map.of("paths",paths));return repo.get(id);
 }
 private void checkEvidence(Map<String,Object> evidence,List<String> ids){TddEvidence.check(evidence,ids);}
 @GetMapping("/api/submissions/{id}") public Map<String,Object> submission(@PathVariable UUID id,HttpServletRequest request){var row=repo.get(id);var member=member(request);if(!member.admin()&&!member.id().equals(row.get("member_id")))throw new GateException(403,"ACCESS_DENIED");return row;}
 @GetMapping("/api/submissions/{id}/patch") public Map<String,String> patch(@PathVariable UUID id,HttpServletRequest request){var row=repo.get(id);var member=member(request);if(!member.admin()&&!member.id().equals(row.get("member_id")))throw new GateException(403,"ACCESS_DENIED");return Map.of("patch",new String(repo.patch(id),StandardCharsets.UTF_8));}
 @org.springframework.transaction.annotation.Transactional @PostMapping("/api/submissions/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@RequestBody Map<String,Object> input,HttpServletRequest request){var admin=admin(request);var row=repo.get(id);if(admin.id().equals(row.get("member_id")))throw new GateException(403,"SELF_REVIEW_FORBIDDEN");String note=Objects.toString(input.get("note"),"");if(!(input.get("approved") instanceof Boolean)||note.length()<4||note.length()>2000)throw new GateException(400,"REVIEW_CONFIRMATION_REQUIRED");if(!repo.review(id,admin.id(),note,Boolean.TRUE.equals(input.get("approved"))))throw new GateException(409,"STATE_CHANGED");repo.event(id,admin.id(),"HUMAN_REVIEW",Map.of("note",note,"approved",input.get("approved")));return repo.get(id);}
 @PostMapping("/api/submissions/{id}/verify") public ResponseEntity<?> verify(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> input,HttpServletRequest request){admin(request);jobs.verify(id);return ResponseEntity.accepted().body(Map.of("state","VERIFYING"));}
 @GetMapping("/api/schedule") public Map<String,Object> schedule(HttpServletRequest request){admin(request);var result=new LinkedHashMap<>(repo.schedule());result.put("enabled",settings.enabled());result.put("interval_seconds",settings.interval());return result;}
 @PostMapping("/api/sync") public Map<String,Object> sync(@RequestBody(required=false) Map<String,Object> input,HttpServletRequest request){admin(request);jobs.sync();return Map.of("state","SYNC_FINISHED");}
 @ExceptionHandler(GateException.class) public ResponseEntity<?> error(GateException error){return ResponseEntity.status(error.status).body(Map.of("code",error.code));}
 @ExceptionHandler({IllegalArgumentException.class,ClassCastException.class}) public ResponseEntity<?> invalid(Exception error){return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST"));}
}
