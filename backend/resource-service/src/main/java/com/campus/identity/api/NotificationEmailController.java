package com.campus.identity.api;
import com.campus.identity.application.NotificationEmailService;import com.campus.common.security.IdentityPrincipal;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.UUID;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;import org.springframework.http.ResponseEntity;
@RestController
public class NotificationEmailController {
 private final NotificationEmailService service;public NotificationEmailController(NotificationEmailService service){this.service=service;}
 public record EmailInput(@NotBlank @Email @Size(max=254) String email){@Override public String toString(){return "EmailInput[REDACTED]";}}
 public record VerifyInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){@Override public String toString(){return "VerifyInput[REDACTED]";}}
 @GetMapping("/api/account/email") @PreAuthorize("hasAuthority('identity:read')") public NotificationEmailService.Profile profile(@AuthenticationPrincipal IdentityPrincipal user){return service.profile(user.id());}
 @PatchMapping("/api/account/email") @PreAuthorize("hasAuthority('identity:read')") public NotificationEmailService.Profile save(@AuthenticationPrincipal IdentityPrincipal user,@Valid @RequestBody EmailInput input){return service.save(user.id(),input.email());}
 @PostMapping("/api/account/email/verification") @PreAuthorize("hasAuthority('identity:read')") public ResponseEntity<NotificationEmailService.Receipt> request(@AuthenticationPrincipal IdentityPrincipal user){var receipt=service.request(user.id());return ResponseEntity.status(receipt.status().equals("UNKNOWN")?202:200).body(receipt);}
 @PostMapping("/api/account/email/verify") @PreAuthorize("hasAuthority('identity:read')") public NotificationEmailService.Profile verify(@AuthenticationPrincipal IdentityPrincipal user,@Valid @RequestBody VerifyInput input){return service.verify(user.id(),input.token());}
 @GetMapping("/internal/identity/users/{id}/notification-email") public NotificationEmailService.Address address(@PathVariable UUID id){return service.address(id);}
}
