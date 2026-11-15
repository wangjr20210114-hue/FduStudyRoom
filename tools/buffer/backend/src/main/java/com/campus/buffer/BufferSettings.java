package com.campus.buffer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public class BufferSettings {
 public record Member(String id,String gitee,String layer,List<String> modules,boolean admin,String tokenHash) {
   List<String> layers() { return "fullstack".equals(layer)?List.of("backend","frontend"):List.of(layer); }
   Member scoped(String selected) {
     if(selected==null || selected.isBlank())return this;
     if(!layers().contains(selected))throw new GateException(403,"LAYER_NOT_ASSIGNED");
     return new Member(id,gitee,selected,modules,admin,tokenHash);
   }
   Map<String,Object> publicView() { return Map.of("id",id,"gitee",gitee==null?"":gitee,"layer",layer,"modules",modules,"admin",admin); }
 }
 private final Map<String,Object> config;
 private final Path path;
 private final Map<String,Object> manifest;
 private final List<Member> members;
 private final List<Map<String,Object>> requirements;
 @SuppressWarnings("unchecked")
 public BufferSettings(@Value("${campus.buffer.config}") String name) throws Exception {
   if(name.isBlank())throw new IllegalArgumentException("CAMPUS_BUFFER_CONFIG_REQUIRED");
   path=Path.of(name).toAbsolutePath();
   var json=JsonMapper.builder().build();
   config=json.readValue(Files.readString(path),Map.class);
   manifest=json.readValue(Files.readString(root().resolve("spec/modules.json")),Map.class);
   requirements=(List<Map<String,Object>>)json.readValue(Files.readString(root().resolve("spec/requirements.json")),Map.class).get("requirements");
   members=((List<Map<String,Object>>)config.get("members")).stream().map(m->new Member((String)m.get("id"),(String)m.get("gitee"),(String)m.get("layer"),(List<String>)m.get("modules"),Boolean.TRUE.equals(m.get("admin")),(String)m.get("tokenHash"))).toList();
   if(new HashSet<>(members.stream().map(Member::id).toList()).size()!=members.size())throw new IllegalArgumentException("DUPLICATE_MEMBER");
   Set<String> ownership=new HashSet<>();
   Set<String> hashes=new HashSet<>();
   for(Member member:members){
     if(!hashes.add(member.tokenHash()))throw new IllegalArgumentException("DUPLICATE_MEMBER_TOKEN");
     if(!member.admin())for(String module:member.modules()){
       for(String layer:member.layers()){
         if(!Set.of("frontend","backend").contains(layer))throw new IllegalArgumentException("INVALID_MEMBER_LAYER");
         allowed(member.scoped(layer),module);
         if(!ownership.add(module+":"+layer))throw new IllegalArgumentException("DUPLICATE_MODULE_LAYER_OWNER");
       }
     }
   }
   if(interval()<259200)throw new IllegalArgumentException("INTERVAL_BELOW_72_HOURS");
   if(!Double.isFinite(memoryThreshold()) || memoryThreshold()<=0 || memoryThreshold()>=100)throw new IllegalArgumentException("INVALID_MEMORY_THRESHOLD");
   if(pressureCooldown()<60)throw new IllegalArgumentException("PRESSURE_COOLDOWN_TOO_SHORT");
   for(String origin:allowedOrigins()){
    var uri=java.net.URI.create(origin);
    if(uri.getHost()==null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getRawUserInfo()!=null || (uri.getRawPath()!=null && !uri.getRawPath().isEmpty()) || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && Set.of("127.0.0.1","localhost").contains(uri.getHost()))))throw new IllegalArgumentException("INVALID_ALLOWED_ORIGIN");
   }
 }
 public Path root(){return Path.of((String)config.get("root"));}
 public Path path(){return path;}
 public String baseline(){return (String)config.get("baseline");}
 public boolean enabled(){return Boolean.TRUE.equals(config.get("enabled"));}
 public int interval(){return ((Number)config.getOrDefault("intervalSeconds",259200)).intValue();}
 public boolean verificationEnabled(){return !Boolean.FALSE.equals(config.get("verificationEnabled"));}
 public double memoryThreshold(){return ((Number)config.getOrDefault("memoryThresholdPercent",85)).doubleValue();}
 public int pressureCooldown(){return ((Number)config.getOrDefault("pressureCooldownSeconds",300)).intValue();}
 public long minCiAvailableBytes(){return ((Number)config.getOrDefault("minCiAvailableBytes",2684354560L)).longValue();}
 public String memoryInfoPath(){return (String)config.getOrDefault("memoryInfoPath","");}
 @SuppressWarnings("unchecked") public Set<String> allowedOrigins(){return Set.copyOf((List<String>)config.getOrDefault("allowedOrigins",List.of("http://127.0.0.1:7780","http://localhost:7780","http://127.0.0.1:5174","http://localhost:5174")));}
 public String python(){return (String)config.getOrDefault("pythonExecutable",System.getProperty("os.name").startsWith("Windows")?"python":"python3");}
 public List<Map<String,Object>> requirements(){return requirements;}
 public Map<String,Object> manifest(){return manifest;}
 public Member authenticate(String bearer) {
   if(bearer==null || !bearer.startsWith("Bearer ") || bearer.length()>512)return null;
   String digest=sha(bearer.substring(7).getBytes(StandardCharsets.UTF_8));
   return members.stream().filter(m->MessageDigest.isEqual(m.tokenHash().getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII))).findFirst().orElse(null);
 }
 public static String sha(byte[] value) {
   try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 @SuppressWarnings("unchecked")
 public List<String> allowed(Member member,String module) {
   if(!member.modules().contains(module))throw new GateException(403,"MODULE_NOT_ASSIGNED");
   return ((List<Map<String,Object>>)manifest.get("modules")).stream().filter(m->module.equals(m.get("id")))
    .map(m->(List<String>)((Map<String,Object>)m.get("write_paths")).get(member.layer())).filter(Objects::nonNull).findFirst().orElseThrow(()->new GateException(403,"MODULE_NOT_ASSIGNED"));
 }
}
