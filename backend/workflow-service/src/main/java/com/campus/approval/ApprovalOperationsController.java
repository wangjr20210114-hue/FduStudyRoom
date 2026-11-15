package com.campus.approval;
import com.campus.common.security.IdentityPrincipal;import java.util.*;import org.springframework.http.ResponseEntity;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController public class ApprovalOperationsController{
 final ApprovalEngine e;public ApprovalOperationsController(ApprovalEngine e){this.e=e;}
 @PostMapping("/api/approvals/requests") public Object submit(@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b){var result=e.submit(a,b);boolean duplicate=Boolean.TRUE.equals(result.remove("_duplicate"));return ResponseEntity.status(duplicate?200:201).body(result);}
 @GetMapping("/api/approvals/requests") public Object list(@AuthenticationPrincipal IdentityPrincipal a,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return e.list(a,false,page,size);}
 @GetMapping("/api/approvals/tasks") public Object tasks(@AuthenticationPrincipal IdentityPrincipal a,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return e.list(a,true,page,size);}
 @PostMapping("/api/approvals/requests/{id}/cancel") public Object cancel(@PathVariable UUID id,@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b){return e.cancel(id,a,b);}
 @GetMapping("/api/approvals/configuration") public Object config(@AuthenticationPrincipal IdentityPrincipal a){return e.configuration(a);}
 @PutMapping("/api/approvals/configuration") public Object config(@AuthenticationPrincipal IdentityPrincipal a,@RequestBody Map<String,Object>b){return e.saveConfiguration(a,b);}
 @GetMapping("/api/approvals/candidates") public Object candidates(@AuthenticationPrincipal IdentityPrincipal a){return e.candidates(a);}
 @PostMapping("/internal/approvals/maintenance-events") public Object maintenance(@RequestBody Map<String,Object>b){return e.maintenance(b);}
}
