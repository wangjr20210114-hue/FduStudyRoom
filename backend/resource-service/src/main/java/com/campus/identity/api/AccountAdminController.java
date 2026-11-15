package com.campus.identity.api;

import com.campus.common.security.IdentityPrincipal;
import com.campus.identity.application.IdentityService;
import com.campus.identity.infrastructure.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AccountAdminController {
    private final AccountRepository accounts;
    private final IdentityService identities;
    public AccountAdminController(AccountRepository accounts, IdentityService identities) { this.accounts = accounts; this.identities = identities; }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('identity:roles:read')")
    List<AccountRepository.Role> roles() { return accounts.roles(); }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('identity:users:read')")
    List<AccountRepository.UserRow> users() { return accounts.listUsers(); }

    public record AccessUpdate(@NotNull UUID roleId, @NotNull Boolean enabled, @Positive int expectedAuthVersion) {}
    @PatchMapping("/users/{id}/access")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity:users:update')")
    void access(@PathVariable UUID id, @Valid @RequestBody AccessUpdate update, @AuthenticationPrincipal IdentityPrincipal principal) {
        identities.changeAccess(principal.id(), id, update.roleId(), update.enabled(), update.expectedAuthVersion());
    }
}
