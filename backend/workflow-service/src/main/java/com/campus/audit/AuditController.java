package com.campus.audit;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class AuditController {
 private final AuditStore store;public AuditController(AuditStore store){this.store=store;}
 @PostMapping("/internal/audit/events") ResponseEntity<AuditStore.Receipt> accept(@Valid @RequestBody AuditEvent event){var receipt=store.accept(event);return ResponseEntity.status(receipt.duplicate()?200:201).body(receipt);}
 @GetMapping("/api/audit") @PreAuthorize("hasAuthority('audit:read')")
 Map<String,Object> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String traceId,@RequestParam(required=false) String sourceModule,@RequestParam(required=false) UUID sourceId){return store.list(page,size,traceId,sourceModule,sourceId);}
}
