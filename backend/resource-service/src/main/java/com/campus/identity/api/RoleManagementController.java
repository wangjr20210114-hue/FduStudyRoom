package com.campus.identity.api;

import com.campus.identity.application.RoleManagementService;
import com.campus.identity.infrastructure.RoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class RoleManagementController {
    private final RoleManagementService roles;
    private final RoleRepository repository;
    public RoleManagementController(RoleManagementService roles,RoleRepository repository) { this.roles=roles;this.repository=repository; }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('identity:roles:read')")
    public List<RoleRepository.Permission> permissions() { return repository.permissions(); }

    public record NewRole(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String code,
            @NotBlank @Size(max=64) String name,@NotNull Set<@NotBlank @Size(max=128) String> permissions) {}
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('identity:roles:write')")
    public RoleRepository.RoleDetails create(@Valid @RequestBody NewRole input) { return roles.create(input.code(),input.name(),input.permissions()); }

    public record PermissionUpdate(@NotNull Set<@NotBlank @Size(max=128) String> permissions) {}
    @PutMapping("/roles/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity:roles:write')")
    public void permissions(@PathVariable UUID id,@Valid @RequestBody PermissionUpdate input) { roles.replacePermissions(id,input.permissions()); }

    public record EnabledUpdate(@NotNull Boolean enabled) {}
    @PatchMapping("/roles/{id}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity:roles:write')")
    public void enabled(@PathVariable UUID id,@Valid @RequestBody EnabledUpdate input) { roles.setEnabled(id,input.enabled()); }
}
