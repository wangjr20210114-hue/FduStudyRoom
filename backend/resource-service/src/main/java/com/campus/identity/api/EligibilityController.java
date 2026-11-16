package com.campus.identity.api;
import com.campus.identity.application.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import org.springframework.http.HttpStatus;
@RestController
public class EligibilityController {
 private final AccountEligibilityService service;public EligibilityController(AccountEligibilityService service){this.service=service;}
 public record Update(@Size(max=64) String departmentCode,@NotNull Boolean canApplyRoom,@Positive int expectedAuthVersion){}
 @PatchMapping("/api/admin/users/{id}/eligibility") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('identity:users:update')")
 public void update(@PathVariable UUID id,@Valid @RequestBody Update input){service.update(id,input.departmentCode(),input.canApplyRoom(),input.expectedAuthVersion());}
 @GetMapping("/internal/identity/users/{id}/eligibility") public AccountEligibilityService.Eligibility eligibility(@PathVariable UUID id){return service.eligibility(id);}
 @GetMapping("/internal/identity/users") public List<AccountEligibilityService.Candidate> candidates(@RequestParam String permission){return service.candidates(permission);}
}
