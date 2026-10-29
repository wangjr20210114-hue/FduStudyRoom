package com.campus.common.security;

import java.util.Set;
import java.util.UUID;

public record IdentityPrincipal(UUID id, String loginName, String displayName, String identityType,
        String identitySource, String role, Set<String> permissions, int authVersion, boolean profileIncomplete) {}
