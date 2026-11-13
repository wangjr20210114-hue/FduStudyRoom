package com.campus.buffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class PatchGate {
 private final BufferSettings settings;
 public PatchGate(BufferSettings settings){this.settings=settings;}
 public List<String> check(BufferSettings.Member member,String module,byte[] patch) {
  var allowed=settings.allowed(member,module);
  if(patch.length==0 || patch.length>4_000_000)throw new GateException(400,"PATCH_SIZE_INVALID");
  String text=new String(patch,StandardCharsets.UTF_8);
  if(text.contains("mode 120000") || text.contains("mode 160000") || text.contains("\nrename from ") || text.contains("\ncopy from "))throw new GateException(403,"LINKS_NOT_ALLOWED");
  try {
   var process=new ProcessBuilder("git","apply","--numstat","-z","--").directory(settings.root().toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
   process.getOutputStream().write(patch);process.getOutputStream().close();
   byte[] output=process.getInputStream().readAllBytes();
   if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new GateException(400,"PATCH_TIMEOUT");}
   if(process.exitValue()!=0)throw new GateException(400,"PATCH_INVALID");
   List<String> paths=new ArrayList<>();
   for(String entry:new String(output,StandardCharsets.UTF_8).split("\u0000")){
    String[] fields=entry.split("\t",3);if(fields.length!=3)throw new GateException(400,"PATCH_PATH_INVALID");
    String path=fields[2];
    if(path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":") || Arrays.stream(path.split("/",-1)).anyMatch(p->p.equals("..")||p.equals(".")||p.isEmpty()) || path.contains("/.git/") || path.contains("/.env") || path.endsWith(".local") || path.endsWith(".log") || allowed.stream().noneMatch(path::startsWith))throw new GateException(403,"PATH_OUTSIDE_MODULE");
    for(Object value:(List<?>)settings.manifest().getOrDefault("protected_paths",List.of())){String prefix=(String)value;if(path.equals(prefix)||(prefix.endsWith("/")&&path.startsWith(prefix)))throw new GateException(403,"FROZEN_BASELINE_PATH");}
    String file=path.substring(path.lastIndexOf('/')+1);String suffix=file.contains(".")?file.substring(file.lastIndexOf('.')).toLowerCase():"";
    if(!file.equals(".gitkeep")){
      var types=member.layer().equals("frontend")?Set.of(".vue",".ts",".css",".json",".md",".svg",".png",".jpg",".jpeg",".webp",".woff2"):Set.of(".java",".sql",".json",".yaml",".yml",".properties",".xml",".csv",".txt",".md");
      if(!types.contains(suffix)||(member.layer().equals("backend")&&path.contains("/java/")&&!suffix.equals(".java"))||(path.startsWith("spec/progress/")&&!suffix.equals(".json")))throw new GateException(403,"BASELINE_LANGUAGE_REQUIRED");
    }
    paths.add(path);
   }
   if(paths.isEmpty())throw new GateException(400,"EMPTY_PATCH");
   return paths;
  } catch(GateException e){throw e;}
  catch(Exception e){throw new GateException(503,"GIT_UNAVAILABLE");}
 }
}
