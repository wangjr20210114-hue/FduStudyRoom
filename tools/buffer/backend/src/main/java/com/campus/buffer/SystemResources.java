package com.campus.buffer;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;
@Component
public class SystemResources {
 private final BufferSettings settings;
 public SystemResources(BufferSettings settings){this.settings=settings;}
 public record Memory(long totalBytes,long availableBytes,boolean known) {
  public Double usedPercent(){return known ? 100.0*(totalBytes-availableBytes)/totalBytes : null;}
 }
 public static Memory parseMemory(String body){
  Map<String,Long> values=new HashMap<>();var matcher=Pattern.compile("(?m)^(MemTotal|MemAvailable):\\s+([0-9]+)\\s+kB\\s*$").matcher(body);
  try{while(matcher.find())values.put(matcher.group(1),Math.multiplyExact(Long.parseLong(matcher.group(2)),1024L));}catch(ArithmeticException|NumberFormatException e){return new Memory(0,0,false);}
  long total=values.getOrDefault("MemTotal",0L),available=values.getOrDefault("MemAvailable",-1L);
  return total>0 && available>=0 && available<=total ? new Memory(total,available,true) : new Memory(0,0,false);
 }
 public Memory memory(){
  String configured=settings.memoryInfoPath();
  Path path=Path.of(configured.isBlank()?"/proc/meminfo":configured);
  if(!configured.isBlank() || Files.exists(path)){
   try{return parseMemory(Files.readString(path));}catch(Exception e){return new Memory(0,0,false);}
  }
  if(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os){
   long total=os.getTotalMemorySize(),available=os.getFreeMemorySize();
   if(total>0 && available>=0 && available<=total)return new Memory(total,available,true);
  }
  return new Memory(0,0,false);
 }
 public boolean pressure(Memory memory){return memory.known() && memory.usedPercent()>=settings.memoryThreshold();}
 public boolean canStartCi(){var m=memory();return m.known() && !pressure(m) && m.availableBytes()>=settings.minCiAvailableBytes();}
 public Map<String,Object> snapshot(){
  var m=memory();Map<String,Object> out=new LinkedHashMap<>();out.put("sampledAt",java.time.Instant.now().toString());
  out.put("memory",m);out.put("memoryUsedPercent",m.usedPercent());out.put("pressure",pressure(m));out.put("thresholdPercent",settings.memoryThreshold());
  out.put("verificationEnabled",settings.verificationEnabled());out.put("ciCanStart",settings.verificationEnabled() && canStartCi());out.put("syncEnabled",settings.enabled());
  out.put("intervalSeconds",settings.interval());out.put("pressureCooldownSeconds",settings.pressureCooldown());
  try{var fs=Files.getFileStore(settings.path().getParent());out.put("disk",Map.of("known",true,"totalBytes",fs.getTotalSpace(),"availableBytes",fs.getUsableSpace()));}
  catch(Exception e){out.put("disk",Map.of("known",false));}
  var runtime=Runtime.getRuntime();out.put("jvm",Map.of("usedBytes",runtime.totalMemory()-runtime.freeMemory(),"maxBytes",runtime.maxMemory()));
  return out;
 }
}
