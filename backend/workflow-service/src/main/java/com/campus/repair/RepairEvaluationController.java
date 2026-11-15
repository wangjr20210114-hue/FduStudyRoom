package com.campus.repair;
import com.campus.common.security.IdentityPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/repairs")
public class RepairEvaluationController {
 private final RepairEvaluationService service;
 public RepairEvaluationController(RepairEvaluationService service){this.service=service;}
 @PostMapping("/{id}/evaluation") RepairEvaluationService.Order evaluate(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal actor,@RequestBody Map<String,Object> body){return service.evaluate(id,actor,body);}
 @PostMapping("/{id}/acceptance") void acceptance(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal actor,@RequestBody Map<String,Object> body){service.acceptance(id,actor,body);}
 @GetMapping("/{id}") RepairEvaluationService.Order detail(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal actor){return service.detail(id,actor);}
 @GetMapping("/{id}/history") List<RepairEvaluationService.Event> history(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal actor){return service.history(id,actor);}
}
