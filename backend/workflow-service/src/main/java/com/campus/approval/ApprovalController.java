package com.campus.approval;

import com.campus.common.security.IdentityPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals/requests")
public class ApprovalController {
    private final ApprovalService service;
    public ApprovalController(ApprovalService service) { this.service = service; }

    @GetMapping("/{requestId}")
    public ApprovalService.ApprovalView read(@PathVariable UUID requestId,
            @AuthenticationPrincipal IdentityPrincipal actor) {
        return service.read(requestId, actor);
    }

    @PostMapping("/{requestId}/tasks/{taskId}/decision")
    public ApprovalService.ApprovalView decide(@PathVariable UUID requestId, @PathVariable UUID taskId,
            @RequestBody ApprovalService.Decision decision, @AuthenticationPrincipal IdentityPrincipal actor) {
        return service.decide(requestId, taskId, decision, actor);
    }
}
