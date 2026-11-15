package com.campus.repair;
import com.campus.common.security.IdentityPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/repairs")
public class RepairFlowController {
 private final RepairFlow flow; public RepairFlowController(RepairFlow flow){this.flow=flow;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Map<String,Object> create(@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b,@RequestAttribute("traceId")String trace){return flow.create(a,b,trace==null?UUID.randomUUID().toString():trace);}
 @GetMapping List<Map<String,Object>> list(@AuthenticationPrincipal IdentityPrincipal a){return flow.list(a);}
 @GetMapping("/candidates") List<?> candidates(@AuthenticationPrincipal IdentityPrincipal a,@RequestParam(defaultValue="repair:maintain")String permission){return flow.candidates(a,permission);}
@PostMapping("/{id}/assignment") Map<String,Object> assignment(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b,@RequestAttribute("traceId")String trace){return flow.command(id,a,"assignment",b,trace==null?UUID.randomUUID().toString():trace);}
@PostMapping("/{id}/start") Map<String,Object> start(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b,@RequestAttribute("traceId")String trace){return flow.command(id,a,"start",b,trace==null?UUID.randomUUID().toString():trace);}
@PostMapping("/{id}/finish") Map<String,Object> finish(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b,@RequestAttribute("traceId")String trace){return flow.command(id,a,"finish",b,trace==null?UUID.randomUUID().toString():trace);}
@PostMapping("/{id}/cancel") Map<String,Object> cancel(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b,@RequestAttribute("traceId")String trace){return flow.command(id,a,"cancel",b,trace==null?UUID.randomUUID().toString():trace);}
@PostMapping("/{id}/cancel-review") Map<String,Object> cancelreview(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b,@RequestAttribute("traceId")String trace){return flow.command(id,a,"cancel-review",b,trace==null?UUID.randomUUID().toString():trace);}
@PostMapping("/{id}/cancel-inspection") Map<String,Object> cancelinspection(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b,@RequestAttribute("traceId")String trace){return flow.command(id,a,"cancel-inspection",b,trace==null?UUID.randomUUID().toString():trace);}
}
