package com.campus.identity.domain;

import com.campus.common.error.ApiException;
import com.campus.common.security.IdentityPrincipal;
import java.util.Set;
import java.util.UUID;

public record Account(UUID id, String loginName, String displayName, String email,
        UUID roleId, String roleCode, boolean roleEnabled, boolean enabled, int authVersion, Set<String> permissions) {
    public void requireEligible() {
        if (!enabled || !roleEnabled) throw new ApiException(403, "ACCOUNT_DISABLED");
    }

    public IdentityPrincipal principal() {
        return new IdentityPrincipal(id, loginName, displayName, roleCode,
                Set.copyOf(permissions), authVersion, email == null || email.isBlank());
    }
}
