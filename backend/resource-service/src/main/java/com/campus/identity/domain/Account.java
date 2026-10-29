package com.campus.identity.domain;

import com.campus.common.error.ApiException;
import com.campus.common.security.IdentityPrincipal;
import java.util.Set;
import java.util.UUID;

public record Account(UUID id, String loginName, String displayName, String email, String identityType,
        String identitySource, boolean identityActive, UUID roleId, String roleCode, boolean roleEnabled,
        boolean enabled, int authVersion, Set<String> permissions) {
    public void requireEligible(String mode) {
        String source = "dev".equals(mode) ? "DEVELOPMENT" : "FUDAN_UIS";
        if (!source.equals(identitySource) || !identityActive) throw new ApiException(403, "IDENTITY_INELIGIBLE");
        if (!enabled || !roleEnabled) throw new ApiException(403, "ACCOUNT_DISABLED");
        if (!"STUDENT".equals(identityType) && !("STAFF".equals(identityType)
                && Set.of("SYSTEM_ADMIN", "MAINTAINER").contains(roleCode))) {
            throw new ApiException(403, "IDENTITY_INELIGIBLE");
        }
    }

    public IdentityPrincipal principal() {
        return new IdentityPrincipal(id, loginName, displayName, identityType, identitySource, roleCode,
                Set.copyOf(permissions), authVersion, email == null || email.isBlank());
    }
}
